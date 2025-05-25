using CefSharp;
using CefSharp.JavascriptBinding;
using CefSharp.ModelBinding;
using CefSharp.Wpf;
using System;
using System.Collections.Generic;
using System.Data.SQLite;
using System.Diagnostics;
using System.IO;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;

namespace LocationTrackerApp_Client
{
    public class Location
    {
        public string PhoneID { get; set; }
        public double Latitude { get; set; }
        public double Longitude { get; set; }
        public long Timestamp { get; set; }
        public double? Raio { get; set; }
        public double? Altitude { get; set; }
        public double? PrecisionAltitude { get; set; }
    }

    public partial class MainWindow : Window
    {
        public MainWindow()
        {
            Debug.WriteLine("InitializeComponent");
            CefSharpSettings.ConcurrentTaskExecution = true;
            InitializeComponent();
            StartGUI();
        }

        private void StartGUI()
        {
            Debug.WriteLine("StartGUI");
            string path = Path.Combine(Directory.GetCurrentDirectory(), "home.html");
            if (File.Exists(path))
            {
                browser.Address = "file:///" + path.Replace("\\", "/");

                browser.JavascriptObjectRepository.Settings.LegacyBindingEnabled = false;

                browser.JavascriptObjectRepository.ResolveObject += (sender, e) =>
                {
                    if (e.ObjectName == "comModule")
                    {
                        var bindingOptions = new BindingOptions
                        {
                            Binder = new DefaultBinder()
                        };

                        e.ObjectRepository.NameConverter = new CamelCaseJavascriptNameConverter();
                        e.ObjectRepository.Register("comModule", new comModule(), options: bindingOptions);
                        Debug.WriteLine("comModule registrado com sucesso!");
                    }
                };

                browser.FrameLoadEnd += async (sender, args) =>
                {
                    if (args.Frame.IsMain)
                    {
                        try
                        {
                            dynamic config = Newtonsoft.Json.JsonConvert.DeserializeObject(File.ReadAllText("config.json"));
                            await browser.EvaluateScriptAsync($"window.APP_CONFIG = {Newtonsoft.Json.JsonConvert.SerializeObject(config)};");
                            await browser.EvaluateScriptAsync("window.initAppFromConfig();");

                            var response = await browser.EvaluateScriptAsync("CefSharp.BindObjectAsync('comModule').then(function() { console.log('comModule vinculado com sucesso!'); }).catch(console.error);");
                            if (!response.Success)
                            {
                                Debug.WriteLine("Erro ao vincular comModule: " + response.Message);
                            }
                            else
                            {
                                Debug.WriteLine("comModule vinculado no JS!");
                                await browser.EvaluateScriptAsync("console.log('Teste de console.log no JS');");
                            }
                        }
                        catch (Exception ex)
                        {
                            Debug.WriteLine("Erro ao enviar config para JS: " + ex.Message);
                        }
                    }
                };

                browser.ConsoleMessage += (sender, e) => Debug.WriteLine($"JS Console: {e.Message}");
            }
            else
            {
                MessageBox.Show("Arquivo home.html não encontrado.");
            }
        }

        public class comModule
        {
            private readonly string dbPath;

            public comModule()
            {
                dbPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "localizacoes.db");

                Debug.WriteLine(">>> DB vai ser criado/aberto em: " + dbPath);

                if (!File.Exists(dbPath))
                    SQLiteConnection.CreateFile(dbPath);

                using var connection = new SQLiteConnection($"Data Source={dbPath};Version=3;");
                connection.Open();
                string sql = @"CREATE TABLE IF NOT EXISTS localizacoes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    phoneID TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    timestamp INTEGER,
                    raio REAL,
                    altitude REAL,
                    precisionAltitude REAL
                );";
                using var cmd = new SQLiteCommand(sql, connection);
                cmd.ExecuteNonQuery();
            }

            public void RegisterFile(string data)
            {
                Debug.WriteLine(data);

                try
                {
                    var options = new JsonSerializerOptions { PropertyNameCaseInsensitive = true };
                    var localizacoes = JsonSerializer.Deserialize<List<Location>>(data, options);

                    if (localizacoes == null || localizacoes.Count == 0)
                    {
                        Debug.WriteLine(">>> registerFile: nada para inserir");
                        return;
                    }
                    Debug.WriteLine($">>> registerFile: recebi {localizacoes.Count} itens");

                    using var connection = new SQLiteConnection($"Data Source={dbPath};Version=3;");
                    connection.Open();
                    using var transaction = connection.BeginTransaction();

                    const string sql = @"
                        INSERT INTO localizacoes
                          (phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude)
                        VALUES
                          (@phoneID, @latitude, @longitude, @timestamp, @raio, @altitude, @precisionAltitude);
                    ";

                    using var cmd = new SQLiteCommand(sql, connection, transaction);

                    foreach (var loc in localizacoes)
                    {
                        Debug.WriteLine($">>> insert: {loc.PhoneID} @ {loc.Timestamp}");
                        cmd.Parameters.Clear();
                        cmd.Parameters.AddWithValue("@phoneID", loc.PhoneID);
                        cmd.Parameters.AddWithValue("@latitude", loc.Latitude);
                        cmd.Parameters.AddWithValue("@longitude", loc.Longitude);
                        cmd.Parameters.AddWithValue("@timestamp", loc.Timestamp);
                        cmd.Parameters.AddWithValue("@raio", loc.Raio.HasValue ? (object)loc.Raio.Value : DBNull.Value);
                        cmd.Parameters.AddWithValue("@altitude", loc.Altitude.HasValue ? (object)loc.Altitude.Value : DBNull.Value);
                        cmd.Parameters.AddWithValue("@precisionAltitude",
                                                    loc.PrecisionAltitude.HasValue
                                                    ? (object)loc.PrecisionAltitude.Value
                                                    : DBNull.Value);

                        cmd.ExecuteNonQuery();
                    }

                    transaction.Commit();
                    Debug.WriteLine($">>> registerFile: inseri {localizacoes.Count} linhas");
                }
                catch (Exception ex)
                {
                    Debug.WriteLine(">>> ERRO registerFile: " + ex);
                    throw;
                }
            }

            public async Task<string> readFileAsJson(string phoneID)
            {
                try
                {
                    using var connection = new SQLiteConnection($"Data Source={dbPath};Version=3;");
                    await connection.OpenAsync();

                    string sql = @"SELECT * FROM localizacoes WHERE phoneID = @phoneID";
                    using var cmd = new SQLiteCommand(sql, connection);
                    cmd.Parameters.AddWithValue("@phoneID", phoneID);

                    using var reader = await cmd.ExecuteReaderAsync();
                    var results = new List<Dictionary<string, object>>();

                    while (await reader.ReadAsync())
                    {
                        var item = new Dictionary<string, object>();
                        for (int i = 0; i < reader.FieldCount; i++)
                            item[reader.GetName(i)] = reader.GetValue(i);
                        results.Add(item);
                    }
                    return JsonSerializer.Serialize(results);
                }
                catch (Exception ex)
                {
                    Debug.WriteLine("Erro em readFileAsJson: " + ex.Message);
                    return "[]";
                }
            }

            public async Task<List<string>> readFileIDs()
            {
                try
                {
                    using var connection = new SQLiteConnection($"Data Source={dbPath};Version=3;");
                    await connection.OpenAsync();

                    string sql = @"SELECT DISTINCT phoneID FROM localizacoes";
                    using var cmd = new SQLiteCommand(sql, connection);

                    using var reader = await cmd.ExecuteReaderAsync();
                    var ids = new List<string>();
                    while (await reader.ReadAsync())
                        ids.Add(reader.IsDBNull(0) ? null : reader.GetString(0));

                    Debug.WriteLine("IDs encontrados no banco: " + string.Join(", ", ids));
                    return ids;
                }
                catch (Exception ex)
                {
                    Debug.WriteLine("Erro em readFileIDs: " + ex.Message);
                    return new List<string>();
                }
            }

            public async Task cefLog(params object[] data)
            {
                if (data == null)
                {
                    Debug.WriteLine("CEF: data é null");
                    return;
                }
                await Task.Run(() =>
                {
                    string logMessage = (data.Length == 1 && data[0] is string)
                        ? data[0].ToString()
                        : JsonSerializer.Serialize(data, new JsonSerializerOptions { WriteIndented = true });
                    Debug.WriteLine("CEF: " + logMessage);
                });
            }

            private static object ToNullableDouble(Dictionary<string, object> dict, string key)
            {
                if (!dict.TryGetValue(key, out var val) || val == null)
                    return DBNull.Value;
                if (val is double d) return d;
                if (val is float f) return (double)f;
                if (double.TryParse(val.ToString(), out double dbl)) return dbl;
                return DBNull.Value;
            }
        }
    }
}

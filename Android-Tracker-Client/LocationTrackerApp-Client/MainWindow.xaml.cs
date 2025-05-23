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
                if (!File.Exists(dbPath))
                    SQLiteConnection.CreateFile(dbPath);

                using var connection = new SQLiteConnection($"Data Source={dbPath};Version=3;");
                connection.Open();
                string sql = @"CREATE TABLE IF NOT EXISTS localizacoes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    phoneID TEXT,
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

            public async Task registerFile(string data)
            {
                try
                {
                    var localizacoes = JsonSerializer.Deserialize<List<Dictionary<string, object>>>(data);
                    if (localizacoes == null || localizacoes.Count == 0) return;

                    using var connection = new SQLiteConnection($"Data Source={dbPath};Version=3;");
                    await connection.OpenAsync();
                    using var transaction = connection.BeginTransaction();

                    string sql = @"INSERT INTO localizacoes (phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude)
                                   VALUES (@phoneID, @latitude, @longitude, @timestamp, @raio, @altitude, @precisionAltitude)";

                    foreach (var loc in localizacoes)
                    {
                        using var cmd = new SQLiteCommand(sql, connection, transaction);
                        cmd.Parameters.AddWithValue("@phoneID", loc.GetValueOrDefault("phoneID")?.ToString());
                        cmd.Parameters.AddWithValue("@latitude", ToNullableDouble(loc, "latitude"));
                        cmd.Parameters.AddWithValue("@longitude", ToNullableDouble(loc, "longitude"));
                        cmd.Parameters.AddWithValue("@timestamp", Convert.ToInt64(loc.GetValueOrDefault("timestamp")));
                        cmd.Parameters.AddWithValue("@raio", ToNullableDouble(loc, "raio"));
                        cmd.Parameters.AddWithValue("@altitude", ToNullableDouble(loc, "altitude"));
                        cmd.Parameters.AddWithValue("@precisionAltitude", ToNullableDouble(loc, "precisionAltitude"));
                        await cmd.ExecuteNonQueryAsync();
                    }
                    transaction.Commit();
                }
                catch (Exception ex)
                {
                    Debug.WriteLine("Erro no registerFile (bulk insert): " + ex.Message);
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
                        ids.Add(reader.GetString(0));

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

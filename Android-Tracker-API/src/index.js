require('dotenv').config();

const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const app = express();
const db = new Database('localizacoes.db');

if (!process.env.AUTH_TOKEN) {
    throw new Error("AUTH_TOKEN not defined in environment");
}
const AUTH_TOKEN = process.env.AUTH_TOKEN;
const PORT = process.env.PORT || 8080;

db.prepare(`
    CREATE TABLE IF NOT EXISTS localizacoes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        phoneID TEXT,
        latitude REAL,
        longitude REAL,
        timestamp INTEGER,
        raio REAL,
        altitude REAL,
        precisionAltitude REAL
    )
`).run();

app.use(cors());
app.use(express.json({ limit: '50mb' }));

function autenticar(req, res, next) {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).send('Não autorizado');
    }
    const token = authHeader.split(' ')[1];
    if (token !== AUTH_TOKEN) {
        return res.status(403).send('Token inválido');
    }
    next();
}

app.post('/api/enviarLocalizacao', (req, res) => {
    const { phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude } = req.body;
    if (latitude != null && longitude != null && timestamp) {
        const stmt = db.prepare(`
            INSERT INTO localizacoes (phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        `);
        stmt.run(phoneID, latitude, longitude, timestamp, raio, altitude, precisionAltitude);
        console.log(`Recebido: ${latitude}, ${longitude} - ${new Date(timestamp).toLocaleString()}`);
        return res.status(200).send('Localização recebida!');
    }
    res.status(400).send('Dados inválidos!');
});

app.get('/api/listarLocalizacoes', autenticar, (req, res) => {
    const page = Math.max(parseInt(req.query.page) || 1, 1);
    const limit = Math.max(parseInt(req.query.limit) || 100, 1);
    const offset = (page - 1) * limit;

    const total = db.prepare(`SELECT COUNT(*) AS count FROM localizacoes`).get().count;
    const localizacoes = db.prepare(`
        SELECT * FROM localizacoes ORDER BY id LIMIT ? OFFSET ?
    `).all(limit, offset);

    console.log(`Página: ${page}, Entradas recebidas: ${localizacoes.length}, Total de páginas: ${Math.ceil(total / limit)}`);

    res.json({
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
        data: localizacoes
    });
});

app.delete('/api/limparLocalizacoesLidas', autenticar, (req, res) => {
    const { ids } = req.body;
    if (!Array.isArray(ids) || ids.length === 0) {
        return res.status(400).send('Você deve fornecer um array de timestamps.');
    }

    const placeholders = ids.map(() => '?').join(',');
    const stmt = db.prepare(`DELETE FROM localizacoes WHERE timestamp IN (${placeholders})`);
    const info = stmt.run(...ids.map(String));
    console.log(`Removidas: ${info.changes} entradas.`);
    res.status(200).send(`Remoção concluída: ${info.changes} entradas removidas.`);
});

app.delete('/api/limparTudo', autenticar, (req, res) => {
    const stmt = db.prepare(`DELETE FROM localizacoes`);
    const info = stmt.run();
    console.log(`Tudo removido: ${info.changes} entradas.`);
    res.status(200).send('Todas as localizações foram removidas!');
});

app.listen(PORT, () => console.log(`Servidor em http://localhost:${PORT}`));

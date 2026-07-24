require('dotenv').config();
const bcrypt = require('bcryptjs');
const pool = require('../config/db');

// Full official list of Nueva Vizcaya's 15 municipalities and their barangays
// (cross-checked against PhilAtlas and Wikipedia, reconciled to the province's
// official total of 275 barangays). A few barangays already seeded under a
// different spelling/short name (e.g. "Sta. Clara", "District I/II",
// "Poblacion Norte/Sur") are intentionally omitted here to avoid duplicate
// entries for the same real place — see BARANGAYS below.
const MUNICIPALITIES = [
  'Alfonso Castañeda', 'Ambaguio', 'Aritao', 'Bagabag', 'Bambang', 'Bayombong',
  'Diadi', 'Dupax del Norte', 'Dupax del Sur', 'Kasibu', 'Kayapa', 'Quezon',
  'Santa Fe', 'Solano', 'Villaverde'
];

const BARANGAYS = {
  'Alfonso Castañeda': ['Abuyo', 'Cauayan', 'Galintuja', 'Lipuga', 'Lublub', 'Pelaway'],
  'Ambaguio': ['Ammueg', 'Camandag', 'Dulli', 'Labang', 'Napo', 'Poblacion', 'Salingsingan', 'Tiblac'],
  'Aritao': [
    'Anayo', 'Baan', 'Balite', 'Banganan', 'Beti', 'Bone North', 'Bone South', 'Calitlitan',
    'Canabuan', 'Canarem', 'Comon', 'Cutar', 'Darapidap', 'Kirang', 'Latar-Nocnoc-San Francisco',
    'Nagcuartelan', 'Ocao-Capiniaan', 'Poblacion', 'Tabueng', 'Tucanon', 'Yaway'
    // "Sta. Clara" already seeded (same place as official "Santa Clara")
  ],
  'Bagabag': [
    'Bakir', 'Baretbet', 'Careb', 'Lantap', 'Murong', 'Nangalisan', 'Paniki', 'Pogonsino',
    'Quirino', 'San Geronimo', 'San Pedro', 'Santa Cruz', 'Santa Lucia', 'Tuao North',
    'Tuao South', 'Villa Coloma', 'Villaros'
  ],
  'Bambang': [
    'Abian', 'Abinganan', 'Aliaga', 'Almaguer North', 'Almaguer South', 'Banggot', 'Barat',
    'Buag', 'Calaocan', 'Dullao', 'Homestead', 'Indiana', 'Mabuslo', 'Macate',
    'Magsaysay Hills', 'Manamtam', 'Mauan', 'Pallas', 'Salinas', 'San Antonio North',
    'San Antonio South', 'San Fernando', 'San Leonardo', 'Santo Domingo', 'Santo Domingo West'
  ],
  'Bayombong': [
    'Bansing', 'Bonfal East', 'Bonfal Proper', 'Bonfal West', 'Buenavista', 'Busilac',
    'Cabuaan', 'Casat', 'District III Poblacion', 'District IV', 'Don Mariano Marcos',
    'Ipil-Cuneg', 'La Torre North', 'La Torre South', 'Luyang', 'Magapuy', 'Magsaysay',
    'Masoc', 'Paitan', 'Salvacion', 'San Nicolas North', 'Santa Rosa', 'Vista Alegre'
    // "District I" / "District II" already seeded (same places as official
    // "Don Domingo Maddela Poblacion" / "Don Tomas Maddela Poblacion")
  ],
  'Diadi': [
    'Ampakling', 'Arwas', 'Balete', 'Bugnay', 'Butao', 'Decabacan', 'Duruarog', 'Escoting',
    'Langca', 'Lurad', 'Nagsabaran', 'Namamparan', 'Pinya', 'Poblacion', 'Rosario',
    'San Luis', 'San Pablo', 'Villa Aurora', 'Villa Florentino'
  ],
  'Dupax del Norte': [
    'Belance', 'Binnuangan', 'Bitnong', 'Bulala', 'Inaban', 'Ineangan', 'Lamo', 'Mabasa',
    'Macabenga', 'Malasin', 'Munguia', 'New Gumiad', 'Oyao', 'Parai', 'Yabbi'
  ],
  'Dupax del Sur': [
    'Abaca', 'Bagumbayan', 'Balsain', 'Banila', 'Biruk', 'Canabay', 'Carolotan', 'Domang',
    'Dopaj', 'Gabut', 'Ganao', 'Kimbutan', 'Kinabuan', 'Lukidnon', 'Mangayang', 'Palabotan',
    'Sanguit', 'Santa Maria', 'Talbek'
  ],
  'Kasibu': [
    'Alimit', 'Alloy', 'Antutot', 'Bilet', 'Binogawan', 'Biyoy', 'Bua', 'Camamasi',
    'Capisaan', 'Catarawan', 'Cordon', 'Didipio', 'Dine', 'Kakiduguen', 'Kongkong', 'Lupa',
    'Macalong', 'Malabing', 'Muta', 'Nantawacan', 'Pacquet', 'Pao', 'Papaya', 'Poblacion',
    'Pudi', 'Seguem', 'Tadji', 'Tokod', 'Wangal', 'Watwat'
  ],
  'Kayapa': [
    'Acacia', 'Alang-Salacsac', 'Amilong Labeng', 'Ansipsip', 'Baan', 'Babadi',
    'Balangabang', 'Balete', 'Banao', 'Besong', 'Binalian', 'Buyasyas', 'Cabalatan-Alang',
    'Cabanglasan', 'Cabayo', 'Castillo Village', 'Kayapa Proper East', 'Kayapa Proper West',
    'Latbang', 'Lawigan', 'Mapayao', 'Nansiakan', 'Pampang', 'Pangawan', 'Pinayag',
    'Pingkian', 'San Fabian', 'Talecabcab', 'Tidang Village', 'Tubongan'
  ],
  'Quezon': [
    'Aurora', 'Baresbes', 'Bonifacio', 'Buliwao', 'Calaocan', 'Caliat', 'Dagupan',
    'Darubba', 'Maasin', 'Maddiangat', 'Nalubbunan', 'Runruno'
  ],
  'Santa Fe': [
    'Atbu', 'Bacneng', 'Balete', 'Baliling', 'Bantinan', 'Baracbac', 'Buyasyas', 'Canabuan',
    'Imugan', 'Malico', 'Poblacion', 'Santa Rosa', 'Sinapaoan', 'Tactac', 'Unib', 'Villa Flores'
  ],
  'Solano': [
    'Aggub', 'Bagahabag', 'Bangaan', 'Bangar', 'Bascaran', 'Communal', 'Concepcion',
    'Curifang', 'Dadap', 'Lactawan', 'Osmeña', 'Pilar D. Galima', 'Quezon', 'Quirino',
    'Roxas', 'San Juan', 'San Luis', 'Tucal', 'Uddiawan', 'Wacal'
    // "Poblacion Norte" / "Poblacion Sur" already seeded (same places as
    // official "Poblacion North" / "Poblacion South")
  ],
  'Villaverde': [
    'Bintawan Norte', 'Bintawan Sur', 'Cabuluan', 'Ibung', 'Nagbitin', 'Ocapon', 'Pieza',
    'Poblacion', 'Sawmill'
  ]
};

async function main() {
  const client = await pool.connect();
  try {
    const municipalityIds = {};
    for (const name of MUNICIPALITIES) {
      const { rows: existing } = await client.query('SELECT id FROM municipalities WHERE name = $1', [name]);
      if (existing.length) {
        municipalityIds[name] = existing[0].id;
      } else {
        const { rows } = await client.query('INSERT INTO municipalities (name) VALUES ($1) RETURNING id', [name]);
        municipalityIds[name] = rows[0].id;
      }
    }

    for (const [municipality, barangays] of Object.entries(BARANGAYS)) {
      for (const barangayName of barangays) {
        await client.query(
          'INSERT INTO barangays (municipality_id, name) VALUES ($1, $2) ON CONFLICT (municipality_id, name) DO NOTHING',
          [municipalityIds[municipality], barangayName]
        );
      }
    }

    const { rows: existingAdmin } = await client.query('SELECT id FROM users WHERE email = $1', ['admin@deafregistry.local']);
    if (!existingAdmin.length) {
      const passwordHash = await bcrypt.hash('Admin@12345', 10);
      await client.query(
        'INSERT INTO users (name, email, password_hash, role) VALUES ($1, $2, $3, $4)',
        ['System Administrator', 'admin@deafregistry.local', passwordHash, 'admin']
      );
      console.log('Seeded admin user: admin@deafregistry.local / Admin@12345');
    }

    console.log('Seed complete.');
  } finally {
    client.release();
    await pool.end();
  }
}

main().catch((err) => {
  console.error('Seed failed:', err);
  process.exit(1);
});

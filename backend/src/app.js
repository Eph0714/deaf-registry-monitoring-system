const express = require('express');
const cors = require('cors');

const authRoutes = require('./routes/auth.routes');
const municipalitiesRoutes = require('./routes/municipalities.routes');
const barangaysRoutes = require('./routes/barangays.routes');
const teachersRoutes = require('./routes/teachers.routes');
const usersRoutes = require('./routes/users.routes');
const deafIndividualsRoutes = require('./routes/deafIndividuals.routes');
const visitsRoutes = require('./routes/visits.routes');
const reportsRoutes = require('./routes/reports.routes');
const adminRoutes = require('./routes/admin.routes');
const settingsRoutes = require('./routes/settings.routes');
const devicesRoutes = require('./routes/devices.routes');
const errorHandler = require('./middleware/errorHandler');

const app = express();

app.use(cors());
app.use(express.json());

app.get('/health', (req, res) => res.json({ status: 'ok' }));

app.use('/api/auth', authRoutes);
app.use('/api/municipalities', municipalitiesRoutes);
app.use('/api/barangays', barangaysRoutes);
app.use('/api/teachers', teachersRoutes);
app.use('/api/users', usersRoutes);
app.use('/api/deaf-individuals', deafIndividualsRoutes);
app.use('/api/visits', visitsRoutes);
app.use('/api/reports', reportsRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/settings', settingsRoutes);
app.use('/api/devices', devicesRoutes);

app.use((req, res) => res.status(404).json({ message: 'Not found' }));
app.use(errorHandler);

module.exports = app;

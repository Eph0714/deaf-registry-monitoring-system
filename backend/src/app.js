const express = require('express');
const cors = require('cors');
const path = require('path');

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
const calendarEventsRoutes = require('./routes/calendarEvents.routes');
const chatRoutes = require('./routes/chat.routes');
const artworkFoldersRoutes = require('./routes/artworkFolders.routes');
const artworksRoutes = require('./routes/artworks.routes');
const artworkRequestsRoutes = require('./routes/artworkRequests.routes');
const notificationsRoutes = require('./routes/notifications.routes');
const errorHandler = require('./middleware/errorHandler');

const app = express();

// Render terminates TLS at a proxy in front of this process.
app.set('trust proxy', true);

app.use(cors());
app.use(express.json());

// Uploaded photos/artworks live on disk at UPLOAD_ROOT (see photoStorage.js / artworkStorage.js
// for why this must be outside the app's own ephemeral build directory on Hostinger) rather
// than a remote object storage service.
const UPLOAD_ROOT = process.env.UPLOAD_ROOT || path.join(__dirname, '..', 'uploads');
app.use('/uploads', express.static(UPLOAD_ROOT));

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
app.use('/api/calendar-events', calendarEventsRoutes);
app.use('/api/chat', chatRoutes);
app.use('/api/artwork-folders', artworkFoldersRoutes);
app.use('/api/artworks', artworksRoutes);
app.use('/api/artwork-requests', artworkRequestsRoutes);
app.use('/api/notifications', notificationsRoutes);

app.use((req, res) => res.status(404).json({ message: 'Not found' }));
app.use(errorHandler);

module.exports = app;

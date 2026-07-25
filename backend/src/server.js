require('dotenv').config();
const app = require('./app');
const { startAuditLogRetention } = require('./utils/auditRetention');
const { startLocationRetention } = require('./utils/locationRetention');

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Deaf Registry API listening on port ${PORT}`);
  startAuditLogRetention();
  startLocationRetention();
});

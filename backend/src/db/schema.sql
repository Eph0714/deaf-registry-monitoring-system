-- Deaf Registry and Monitoring System - MySQL schema

CREATE TABLE IF NOT EXISTS municipalities (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL UNIQUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS barangays (
  id INT AUTO_INCREMENT PRIMARY KEY,
  municipality_id INT NOT NULL,
  name VARCHAR(150) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_barangay_municipality FOREIGN KEY (municipality_id) REFERENCES municipalities(id) ON DELETE CASCADE,
  UNIQUE KEY uq_barangay (municipality_id, name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teachers (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  contact_number VARCHAR(30) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('admin','conductor') NOT NULL DEFAULT 'conductor',
  teacher_id INT NULL,
  photo_url VARCHAR(255) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS deaf_individuals (
  id INT AUTO_INCREMENT PRIMARY KEY,
  uuid VARCHAR(36) NOT NULL UNIQUE,
  full_name VARCHAR(190) NOT NULL,
  photo_url VARCHAR(255) NULL,
  age INT NULL,
  birth_date DATE NULL,
  gender ENUM('Male','Female','Other') NOT NULL,
  barangay_id INT NOT NULL,
  purok VARCHAR(100) NULL,
  municipality_id INT NOT NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  skill_level ENUM('Skilled','Semi-skilled','Natural') NOT NULL DEFAULT 'Natural',
  monitoring_status ENUM('RV','BS','Transferred','Unlocated') NOT NULL DEFAULT 'Unlocated',
  assigned_teacher_id INT NULL,
  assigned_date DATE NULL,
  contact_number VARCHAR(30) NULL,
  email VARCHAR(190) NULL,
  marital_status ENUM('Single','Married','Widowed','Separated','Divorced') NULL,
  emergency_contact_name VARCHAR(150) NULL,
  emergency_contact_number VARCHAR(30) NULL,
  notes TEXT NULL,
  is_deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_by INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_deaf_municipality FOREIGN KEY (municipality_id) REFERENCES municipalities(id),
  CONSTRAINT fk_deaf_barangay FOREIGN KEY (barangay_id) REFERENCES barangays(id),
  CONSTRAINT fk_deaf_teacher FOREIGN KEY (assigned_teacher_id) REFERENCES teachers(id) ON DELETE SET NULL,
  CONSTRAINT fk_deaf_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_deaf_updated_at (updated_at),
  INDEX idx_deaf_municipality (municipality_id),
  INDEX idx_deaf_name (full_name)
) ENGINE=InnoDB;

-- Note: adding columns to an already-existing deaf_individuals table (upgrades, as
-- opposed to fresh installs where the CREATE TABLE above already has these columns)
-- is handled in migrate.js via an INFORMATION_SCHEMA check, not here — this MySQL
-- server (5.6) does not support "ALTER TABLE ... ADD COLUMN IF NOT EXISTS".

CREATE TABLE IF NOT EXISTS visits (
  id INT AUTO_INCREMENT PRIMARY KEY,
  uuid VARCHAR(36) NOT NULL UNIQUE,
  deaf_individual_id INT NOT NULL,
  visit_datetime DATETIME NOT NULL,
  latitude DOUBLE NULL,
  longitude DOUBLE NULL,
  conductor_id INT NULL,
  conductor_name VARCHAR(150) NULL,
  created_by INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_visit_deaf FOREIGN KEY (deaf_individual_id) REFERENCES deaf_individuals(id) ON DELETE CASCADE,
  CONSTRAINT fk_visit_teacher FOREIGN KEY (conductor_id) REFERENCES teachers(id) ON DELETE SET NULL,
  CONSTRAINT fk_visit_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_visit_deaf (deaf_individual_id),
  INDEX idx_visit_updated_at (updated_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS remarks (
  id INT AUTO_INCREMENT PRIMARY KEY,
  uuid VARCHAR(36) NOT NULL UNIQUE,
  visit_id INT NOT NULL,
  user_id INT NULL,
  user_name VARCHAR(150) NULL,
  remark_text TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_remark_visit FOREIGN KEY (visit_id) REFERENCES visits(id) ON DELETE CASCADE,
  CONSTRAINT fk_remark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_remark_visit (visit_id),
  INDEX idx_remark_updated_at (updated_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_devices (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  device_id VARCHAR(100) NOT NULL,
  device_label VARCHAR(150) NULL,
  status ENUM('approved','pending','rejected') NOT NULL DEFAULT 'pending',
  approved_by INT NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_device_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
  UNIQUE KEY uq_user_device (user_id, device_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS teacher_assignment_history (
  id INT AUTO_INCREMENT PRIMARY KEY,
  deaf_individual_id INT NOT NULL,
  old_teacher_id INT NULL,
  new_teacher_id INT NULL,
  changed_by INT NULL,
  reason VARCHAR(255) NULL,
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tah_deaf FOREIGN KEY (deaf_individual_id) REFERENCES deaf_individuals(id) ON DELETE CASCADE,
  CONSTRAINT fk_tah_old_teacher FOREIGN KEY (old_teacher_id) REFERENCES teachers(id) ON DELETE SET NULL,
  CONSTRAINT fk_tah_new_teacher FOREIGN KEY (new_teacher_id) REFERENCES teachers(id) ON DELETE SET NULL,
  CONSTRAINT fk_tah_changed_by FOREIGN KEY (changed_by) REFERENCES users(id) ON DELETE SET NULL,
  INDEX idx_tah_deaf (deaf_individual_id, changed_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS settings (
  `key` VARCHAR(100) NOT NULL PRIMARY KEY,
  `value` VARCHAR(255) NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT IGNORE INTO settings (`key`, `value`) VALUES ('overdue_visit_days', '30');

CREATE TABLE IF NOT EXISTS audit_logs (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NULL,
  action VARCHAR(50) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id INT NULL,
  details TEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

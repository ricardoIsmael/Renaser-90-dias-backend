-- Web Push usa el mismo registro aislado por usuario que Android/iOS. La suscripción JSON puede
-- ser mas larga que un Expo token; la columna ya es TEXT y el request valida hasta 2000 caracteres.
ALTER TYPE renaser.plataforma_push ADD VALUE IF NOT EXISTS 'WEB';

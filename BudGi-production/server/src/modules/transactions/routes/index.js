// modules/transactions/routes/index.js
'use strict';

const router = require('express').Router();
const multer = require('multer');
const auth = require('../../../middleware/auth');
const c = require('../controllers/transactionController');

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    if (!file.originalname.match(/\.(csv|txt)$/i)) {
      return cb(new Error('Only CSV files are allowed'));
    }
    cb(null, true);
  },
});

const scanUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (_req, file, cb) => {
    if (!file.originalname.match(/\.(jpg|jpeg|png|webp)$/i) && !file.mimetype.startsWith('image/')) {
      return cb(new Error('Only images are allowed'));
    }
    cb(null, true);
  },
});

router.use(auth);

router.get('/', c.getTransactions);
router.get('/summary', c.getSummary);
router.post('/import', upload.single('file'), c.importCSV); // MUST be before /:id
router.post('/scan', scanUpload.single('receipt'), c.scanReceipt);
router.post('/', c.createTransaction);
router.put('/:id', c.updateTransaction);
router.delete('/clear', c.clearTransactions);
router.delete('/:id', c.deleteTransaction);

module.exports = router;

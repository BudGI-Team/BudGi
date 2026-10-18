// modules/budgets/routes/index.js
'use strict';

const router = require('express').Router();
const auth = require('../../../middleware/auth');
const c = require('../controllers/budgetController');

router.use(auth);

router.get('/', c.getBudgets);
router.post('/', c.createBudget);
router.put('/:id', c.updateBudget);
router.delete('/:id', c.deleteBudget);

module.exports = router;

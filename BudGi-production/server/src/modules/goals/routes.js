// modules/goals/routes.js
'use strict';

const router = require('express').Router();
const auth = require('../../middleware/auth');
const c = require('./controllers/goalController');

router.use(auth);

router.get('/', c.listGoals);
router.post('/', c.createGoal);
router.put('/:id', c.updateGoal);
router.delete('/:id', c.deleteGoal);

module.exports = router;

'use strict';

const express = require('express');
const router = express.Router();
const spaceController = require('../controllers/spaceController');
const auth = require('../../../middleware/auth');

router.use(auth);

router.post('/', spaceController.createSpace);
router.get('/', spaceController.getSpaces);
router.get('/:id', spaceController.getSpaceDetails);
router.post('/join/:code', spaceController.joinSpace);
router.post('/:id/goals', spaceController.addGoal);

module.exports = router;

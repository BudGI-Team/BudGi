'use strict';

const crypto = require('crypto');
const Space = require('../models/Space');
const socketService = require('../../../utils/socketService');

/**
 * Generate a secure 8-character invite code
 */
const generateInviteCode = () => {
  return crypto.randomBytes(4).toString('hex').toUpperCase();
};

/**
 * Create a new collaborative space
 */
exports.createSpace = async (userId, data) => {
  const { name, type } = data;
  const inviteCode = generateInviteCode();

  const space = await Space.create({
    name,
    type,
    inviteCode,
    members: [{ userId, role: 'ADMIN' }],
    goals: [],
  });

  return space;
};

/**
 * Get all spaces for a user
 */
exports.getUserSpaces = async (userId) => {
  return Space.find({ 'members.userId': userId })
    .populate('members.userId', 'firstName lastName email profileImage')
    .sort({ createdAt: -1 });
};

/**
 * Get space by ID (if user is member)
 */
exports.getSpaceById = async (userId, spaceId) => {
  const space = await Space.findOne({ _id: spaceId, 'members.userId': userId }).populate(
    'members.userId',
    'firstName lastName email profileImage',
  );
  if (!space) {
    throw new Error('Space not found or unauthorized');
  }
  return space;
};

/**
 * Join a space via invite code
 */
exports.joinSpace = async (userId, inviteCode) => {
  const space = await Space.findOne({ inviteCode });
  if (!space) {
    throw new Error('Invalid or expired invite code');
  }

  const isMember = space.members.some((m) => m.userId.toString() === userId.toString());
  if (isMember) {
    return space; // Already a member
  }

  space.members.push({ userId, role: 'MEMBER' });
  await space.save();

  // Populate newly added member to return updated space
  await space.populate('members.userId', 'firstName lastName email profileImage');

  // Emit WebSocket event so UI updates instantly
  socketService.emitToSpace(space, socketService.EVENTS.SPACE_UPDATED, space);

  return space;
};

/**
 * Add a goal to a space
 */
exports.addGoal = async (userId, spaceId, data) => {
  const space = await exports.getSpaceById(userId, spaceId);

  space.goals.push(data);
  await space.save();

  socketService.emitToSpace(space, socketService.EVENTS.SPACE_UPDATED, space);
  return space;
};

import { AnimationState } from '../models/Sprite';

export interface PoseDefinition {
  step: number;
  state: AnimationState;
  name: string;
  description: string;
  angles: {
    spine: number;
    head: number;
    leftShoulder: number;
    leftElbow: number;
    rightShoulder: number;
    rightElbow: number;
    leftHip: number;
    leftKnee: number;
    rightHip: number;
    rightKnee: number;
  };
}

// 40 Authored Keyframe Poses mirroring Stratum's MocapPoses
export const POSE_SCRIPT_POSES: PoseDefinition[] = [
  // IDLE (8 frames)
  ...Array.from({ length: 8 }, (_, i) => ({
    step: i,
    state: 'IDLE' as AnimationState,
    name: `Idle Breathe ${i + 1}`,
    description: 'Upright balanced warrior stance, gentle breathing bob',
    angles: {
      spine: Math.sin(i * 0.8) * 4,
      head: Math.sin(i * 0.8) * 2,
      leftShoulder: 20 + Math.sin(i * 0.8) * 3,
      leftElbow: 35,
      rightShoulder: -20 - Math.sin(i * 0.8) * 3,
      rightElbow: 40,
      leftHip: 10,
      leftKnee: 10,
      rightHip: -10,
      rightKnee: 10,
    }
  })),
  // WALK (12 frames: contact, passing, contact, passing)
  ...Array.from({ length: 12 }, (_, i) => {
    const phase = (i / 12) * Math.PI * 2;
    return {
      step: 8 + i,
      state: 'WALK' as AnimationState,
      name: `Walk Cadence ${i + 1}`,
      description: 'Dynamic stride over terraced earth with rhythmic weapon counterbalance',
      angles: {
        spine: Math.sin(phase) * 5,
        head: -Math.sin(phase) * 3,
        leftShoulder: Math.sin(phase) * 35,
        leftElbow: 30 + Math.abs(Math.sin(phase)) * 15,
        rightShoulder: -Math.sin(phase) * 35,
        rightElbow: 35 + Math.abs(Math.cos(phase)) * 15,
        leftHip: -Math.sin(phase) * 35,
        leftKnee: Math.max(0, Math.sin(phase) * 45),
        rightHip: Math.sin(phase) * 35,
        rightKnee: Math.max(0, -Math.sin(phase) * 45),
      }
    };
  }),
  // ATTACK (10 frames: windup, strike, cleave, recovery)
  ...Array.from({ length: 10 }, (_, i) => {
    let rightArm = 0;
    let spine = 0;
    if (i < 3) {
      // Wind up
      rightArm = -40 - i * 25;
      spine = -10 - i * 5;
    } else if (i < 6) {
      // Strike down
      rightArm = 30 + (i - 3) * 35;
      spine = 15;
    } else {
      // Recovery
      rightArm = 70 - (i - 6) * 20;
      spine = 5;
    }
    return {
      step: 20 + i,
      state: 'ATTACK' as AnimationState,
      name: `Melee Cleave ${i + 1}`,
      description: 'Powerful downward sweep with full hip rotation',
      angles: {
        spine,
        head: 5,
        leftShoulder: -rightArm * 0.4,
        leftElbow: 40,
        rightShoulder: rightArm,
        rightElbow: 50,
        leftHip: 25,
        leftKnee: 20,
        rightHip: -25,
        rightKnee: 35,
      }
    };
  }),
  // HURT (4 frames)
  ...Array.from({ length: 4 }, (_, i) => ({
    step: 30 + i,
    state: 'HURT' as AnimationState,
    name: `Recoil ${i + 1}`,
    description: 'Impact shudder and stagger backward',
    angles: {
      spine: -18 + i * 4,
      head: -12,
      leftShoulder: -20,
      leftElbow: 60,
      rightShoulder: -30,
      rightElbow: 70,
      leftHip: -15,
      leftKnee: 30,
      rightHip: 15,
      rightKnee: 10,
    }
  })),
  // DIE (6 frames)
  ...Array.from({ length: 6 }, (_, i) => ({
    step: 34 + i,
    state: 'DIE' as AnimationState,
    name: `Fallen ${i + 1}`,
    description: 'Collapse onto the laterite ground',
    angles: {
      spine: 20 + i * 14,
      head: 30 + i * 10,
      leftShoulder: 30 + i * 10,
      leftElbow: 80,
      rightShoulder: 20 + i * 15,
      rightElbow: 85,
      leftHip: 40 + i * 8,
      leftKnee: 60 + i * 5,
      rightHip: 35 + i * 8,
      rightKnee: 65 + i * 5,
    }
  }))
];

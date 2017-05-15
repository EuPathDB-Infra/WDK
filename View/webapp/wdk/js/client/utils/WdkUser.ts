import {AnswerSpec} from './WdkModel';

export interface User {
  id: number;
  email: string;
  isGuest: boolean;
  properties: Record<string,string>
}

export interface UserPreferences {
  [key: string]: string;
}

export interface Step {
  answerSpec: AnswerSpec;
  customName: string;
  description: string;
  displayName: string;
  estimatedSize: number;
  hasCompleteStepAnalyses: boolean;
  id: number;
  ownerId: number;
  recordClassName: string;
  shortDisplayName: string;
  strategyId: number;
}

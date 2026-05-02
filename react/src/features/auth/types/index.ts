/** Auth feature 전용 타입 */

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
}

export interface UserProfile {
  id: number;
  email: string;
  role: string;
  createdAt: string;
}

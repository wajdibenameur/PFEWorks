export interface UserProfile {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  address: string;
  city?: string;
  zipCode?: string;
  phone?: string;
  avatar?: string;
  position?: string;
  roles: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  email: string;
  address: string;
  city?: string;
  zipCode?: string;
  phone?: string;
  avatar?: string;
  position?: string;
}

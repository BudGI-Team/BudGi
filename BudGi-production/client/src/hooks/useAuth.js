// hooks/useAuth.js
// FIX: backend envelope is { success, data: { token, user }, message }
// axios api.js returns r.data (the envelope), so we read envelope.data.token/user

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { authApi } from '../lib/api';
import useStore from '../store/useStore';

export function useLogin() {
  const { setAuth } = useStore();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (envelope) => {
      const data = envelope?.data ?? envelope ?? {};
      if (data?.requiresPhoneVerification || data?.status === 'PHONE_VERIFICATION_REQUIRED') {
        // Handled at page level with PhoneVerificationModal
        return;
      }
      const { token, user } = data;
      if (!token || !user) {
        toast.error('Login failed — unexpected server response');
        return;
      }
      setAuth(user, token);
      toast.success(`Welcome back, ${user.name?.split(' ')[0] ?? 'there'}!`);
      navigate('/app/dashboard');
    },
    onError: (err) => toast.error(err.userMessage || 'Login failed'),
  });
}

export function useRegister() {
  const { setAuth } = useStore();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: authApi.register,
    onSuccess: (envelope) => {
      const data = envelope?.data ?? envelope ?? {};
      if (data?.requiresPhoneVerification || data?.status === 'PHONE_VERIFICATION_REQUIRED') {
        // Handled at page level with PhoneVerificationModal
        return;
      }
      const { token, user } = data;
      if (!token || !user) {
        toast.error('Registration failed — unexpected server response');
        return;
      }
      setAuth(user, token);
      toast.success('Account created! Welcome to BudGi.');
      navigate('/onboarding');
    },
    onError: (err) => toast.error(err.userMessage || 'Registration failed'),
  });
}

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: authApi.me,
    select: (envelope) => envelope?.data?.user ?? null,
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useUpdateProfile() {
  const { updateUser } = useStore();
  const qc = useQueryClient();

  return useMutation({
    mutationFn: authApi.updateProfile,
    onSuccess: (envelope) => {
      // FIX: read from envelope.data.user
      const user = envelope?.data?.user ?? null;
      if (user) {
        updateUser(user);
        qc.setQueryData(['me'], envelope);
      }
      toast.success('Profile updated');
    },
    onError: (err) => toast.error(err.userMessage || 'Update failed'),
  });
}

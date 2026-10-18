// hooks/useAI.js
// All AI features powered by Groq via the backend /api/ai/* endpoints.
// No direct LLM API calls from the client — all routed through the server.

import { useQuery, useMutation } from '@tanstack/react-query';
import { aiApi, analyticsApi } from '../lib/api';

// ── Analytics summary (dashboard data) ───────────────────────────────────────
export function useAnalytics(params = {}) {
  return useQuery({
    queryKey: ['analytics', params],
    queryFn: () => analyticsApi.summary(params),
    select: (envelope) => envelope?.data ?? {},
    staleTime: 60_000,
    retry: 1,
  });
}

// ── Dashboard (summary + prediction + alerts in one call) ─────────────────────
export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: analyticsApi.dashboard,
    select: (envelope) => envelope?.data ?? {},
    staleTime: 60_000,
  });
}

// ── AI insights ───────────────────────────────────────────────────────────────
export function useInsights() {
  return useQuery({
    queryKey: ['ai', 'insights'],
    queryFn: aiApi.insights,
    select: (envelope) => envelope?.data ?? {},
    staleTime: 5 * 60_000,
    retry: 1,
  });
}

// ── AI suggestions ────────────────────────────────────────────────────────────
export function useSuggestions() {
  return useQuery({
    queryKey: ['ai', 'suggestions'],
    queryFn: aiApi.suggestions,
    select: (envelope) => envelope?.data ?? {},
    staleTime: 5 * 60_000,
  });
}

// ── Spending prediction ───────────────────────────────────────────────────────
export function useSpendingPrediction(params = {}) {
  return useQuery({
    queryKey: ['ai', 'predict', params],
    queryFn: () => analyticsApi.predict(params),
    select: (envelope) => envelope?.data ?? {},
    staleTime: 10 * 60_000,
  });
}

// ── Health score ──────────────────────────────────────────────────────────────
export function useHealthScore() {
  return useQuery({
    queryKey: ['ai', 'health-score'],
    queryFn: aiApi.healthScore,
    select: (envelope) => envelope?.data ?? {},
    staleTime: 10 * 60_000,
  });
}

// ── Predict category (transaction categorisation) ─────────────────────────────
export function usePredictCategory() {
  return useMutation({
    mutationFn: (description) => aiApi.predictCategory(description),
  });
}

// ── Analyze with user message ─────────────────────────────────────────────────
export function useAnalyze() {
  return useMutation({
    mutationFn: (userMessage) => aiApi.analyze(userMessage),
  });
}

// ── Copilot Chat (Groq-powered via backend /api/ai/chat) ──────────────────────
// This replaces the old useClaudeChat that called Anthropic directly.
// The backend enriches chat with the user's real financial data (transactions,
// budgets, spending summaries) before sending to Groq's llama3-70b model.
export function useCopilotChat() {
  return useMutation({
    mutationFn: async (messages) => {
      const envelope = await aiApi.chat(messages);
      // Backend returns { success, data: { reply, executedTools } }
      return {
        reply: envelope?.data?.reply ?? "Sorry, I couldn't generate a response.",
        executedTools: envelope?.data?.executedTools ?? [],
      };
    },
  });
}

// ── Chat History ─────────────────────────────────────────────────────────────
export function useChatHistory() {
  return useQuery({
    queryKey: ['ai', 'chat', 'history'],
    queryFn: aiApi.getHistory,
    select: (envelope) => envelope?.data?.messages ?? [],
    staleTime: 5 * 60_000,
  });
}

export function useClearChatHistory() {
  return useMutation({
    mutationFn: aiApi.clearHistory,
  });
}

// ── Smart Budget Suggestions ─────────────────────────────────────────────────
export function useSmartBudgets() {
  return useQuery({
    queryKey: ['ai', 'smart-budgets'],
    queryFn: aiApi.smartBudgets,
    select: (envelope) => envelope?.data ?? {},
    staleTime: 5 * 60_000,
    enabled: true,
  });
}

// ── Backward-compat alias ─────────────────────────────────────────────────────
// AIChat.jsx imports useClaudeChat — alias it to useCopilotChat
export const useClaudeChat = useCopilotChat;

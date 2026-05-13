/**
 * Frontend slash-command registry and parsing helpers.
 *
 * Modelled on openclaw/ui/src/ui/chat/slash-commands.ts but scoped down to
 * what the /api/ws/chat protocol actually exposes — every command here maps
 * either to a `useChatWs()` action or to a local UI side-effect.
 */

import type { ThinkingLevel } from '../types/ws';

export type SlashCommandCategory = 'session' | 'model' | 'tools';

export interface SlashCommandDef {
  name: string;
  aliases?: string[];
  description: string;
  /** Usage hint shown after the name (e.g. "<id>" or "[level]"). */
  args?: string;
  category: SlashCommandCategory;
  /** Fixed argument choices shown when the user begins typing the arg. */
  argOptions?: readonly string[];
}

export const THINKING_LEVELS: readonly ThinkingLevel[] = [
  'off',
  'minimal',
  'low',
  'medium',
  'high',
  'xhigh',
];

export const SLASH_COMMANDS: readonly SlashCommandDef[] = [
  // session
  { name: 'help', description: 'Show available slash commands', category: 'tools' },
  { name: 'clear', description: 'Clear chat history in this view (local only)', category: 'session' },
  {
    name: 'new',
    aliases: ['reset'],
    description: 'Reset the conversation on the server and start a fresh session',
    category: 'session',
  },
  {
    name: 'abort',
    aliases: ['stop'],
    description: 'Abort the in-flight agent turn',
    category: 'session',
  },
  { name: 'state', description: 'Show conversation id, model, thinking level, message count', category: 'session' },
  { name: 'history', description: 'Re-pull persisted history from the server', category: 'session' },

  // model
  {
    name: 'model',
    description: 'Show current model, or switch to <id>',
    args: '[id]',
    category: 'model',
  },
  {
    name: 'think',
    aliases: ['thinking'],
    description: 'Show current thinking level, or set to <level>',
    args: '[level]',
    argOptions: THINKING_LEVELS,
    category: 'model',
  },

  // tools / discovery
  { name: 'skills', description: 'List installed skill packs', category: 'tools' },
  {
    name: 'templates',
    aliases: ['prompts'],
    description: 'List prompt templates available to the agent',
    category: 'tools',
  },
];

export interface ParsedSlashCommand {
  command: SlashCommandDef;
  args: string;
}

/** Parse "/foo bar baz" → { command: foo, args: "bar baz" }. Returns null when input is not a slash command or no command matches. */
export function parseSlashCommand(text: string): ParsedSlashCommand | null {
  const trimmed = text.trim();
  if (!trimmed.startsWith('/')) return null;
  const body = trimmed.slice(1);
  const firstSpace = body.search(/\s/u);
  const name = firstSpace === -1 ? body : body.slice(0, firstSpace);
  const args = firstSpace === -1 ? '' : body.slice(firstSpace + 1).trim();
  if (!name) return null;
  const lower = name.toLowerCase();
  const command = SLASH_COMMANDS.find(
    (c) => c.name === lower || c.aliases?.includes(lower),
  );
  return command ? { command, args } : null;
}

/** Filter+sort commands by the prefix the user has typed after the leading `/`. Used by the autocomplete dropdown. */
export function getSlashCommandCompletions(filter: string): SlashCommandDef[] {
  const lower = filter.trim().toLowerCase();
  const list = lower
    ? SLASH_COMMANDS.filter(
        (c) =>
          c.name.startsWith(lower) ||
          c.aliases?.some((a) => a.startsWith(lower)) ||
          c.description.toLowerCase().includes(lower),
      )
    : [...SLASH_COMMANDS];

  const CATEGORY_ORDER: SlashCommandCategory[] = ['session', 'model', 'tools'];
  return list.sort((a, b) => {
    const ci = CATEGORY_ORDER.indexOf(a.category) - CATEGORY_ORDER.indexOf(b.category);
    if (ci !== 0) return ci;
    if (lower) {
      const ax = a.name.startsWith(lower) ? 0 : 1;
      const bx = b.name.startsWith(lower) ? 0 : 1;
      if (ax !== bx) return ax - bx;
    }
    return a.name.localeCompare(b.name);
  });
}

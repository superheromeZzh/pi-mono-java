/**
 * Slash-command executor. Each command returns markdown-ish text that the
 * caller renders as a "system" chat bubble. Errors are caught and surfaced as
 * the bubble body so a failing command never blocks the UI.
 *
 * Modelled on openclaw/ui/src/ui/chat/slash-command-executor.ts.
 */

import type { useChatWs } from '../composables/useChatWs';
import type { NamedEntry, ThinkingLevel } from '../types/ws';
import {
  SLASH_COMMANDS,
  THINKING_LEVELS,
  type ParsedSlashCommand,
  type SlashCommandCategory,
} from './slashCommands';

type Chat = ReturnType<typeof useChatWs>;

export interface SlashCommandResult {
  /** Body of the bubble. */
  content: string;
  /** isError styles the bubble red. */
  isError?: boolean;
}

const CATEGORY_LABELS: Record<SlashCommandCategory, string> = {
  session: 'Session',
  model: 'Model',
  tools: 'Tools',
};

function formatHelp(): string {
  const grouped = new Map<SlashCommandCategory, typeof SLASH_COMMANDS[number][]>();
  for (const c of SLASH_COMMANDS) {
    const arr = grouped.get(c.category) ?? [];
    arr.push(c);
    grouped.set(c.category, arr);
  }
  const lines: string[] = ['Available commands:'];
  for (const cat of ['session', 'model', 'tools'] as SlashCommandCategory[]) {
    const entries = grouped.get(cat);
    if (!entries?.length) continue;
    lines.push('', `[${CATEGORY_LABELS[cat]}]`);
    for (const c of entries) {
      const usage = c.args ? ` ${c.args}` : '';
      const aliases = c.aliases?.length ? ` (aliases: ${c.aliases.map((a) => '/' + a).join(', ')})` : '';
      lines.push(`  /${c.name}${usage} — ${c.description}${aliases}`);
    }
  }
  lines.push('', 'Tip: type "/" to open the suggestion menu.');
  return lines.join('\n');
}

function formatNamedEntries(title: string, entries: NamedEntry[]): string {
  if (!entries.length) return `${title}: (none)`;
  const lines: string[] = [`${title} (${entries.length}):`];
  for (const e of entries) {
    const src = e.source ? ` [${e.source}]` : '';
    const desc = e.description ? ` — ${e.description}` : '';
    lines.push(`  • ${e.name}${src}${desc}`);
  }
  return lines.join('\n');
}

function normalizeThinkingLevel(raw: string): ThinkingLevel | null {
  const lower = raw.trim().toLowerCase();
  return (THINKING_LEVELS as readonly string[]).includes(lower) ? (lower as ThinkingLevel) : null;
}

export async function executeSlashCommand(
  parsed: ParsedSlashCommand,
  chat: Chat,
): Promise<SlashCommandResult> {
  const { command, args } = parsed;
  try {
    switch (command.name) {
      case 'help':
        return { content: formatHelp() };

      case 'clear':
        chat.clearMessages();
        return { content: 'Chat cleared (local view only — server history preserved).' };

      case 'new':
        await chat.newSession();
        return { content: 'Started a new session on the server.' };

      case 'abort':
        await chat.abort();
        return { content: 'Abort requested.' };

      case 'state': {
        const s = await chat.getState();
        if (!s) return { content: 'Failed to read state.', isError: true };
        return {
          content: [
            `conversation_id: ${s.conversation_id}`,
            `model:           ${s.model ?? '(none)'}`,
            `thinking:        ${s.thinkingLevel}`,
            `streaming:       ${s.isStreaming}`,
            `messageCount:    ${s.messageCount}`,
          ].join('\n'),
        };
      }

      case 'history':
        await chat.getHistory();
        return { content: 'History reloaded from server.' };

      case 'model': {
        if (!args) {
          const current = chat.model.value ?? '(none)';
          const avail = chat.availableModels.value;
          const lines = [`Current model: ${current}`];
          if (avail.length) {
            lines.push('', `Available (${avail.length}):`);
            for (const m of avail.slice(0, 30)) {
              const lock = m.hasCredentials === false ? ' 🔒' : '';
              lines.push(`  • ${m.id} — ${m.name} (${m.provider})${lock}`);
            }
            if (avail.length > 30) lines.push(`  … +${avail.length - 30} more`);
          }
          return { content: lines.join('\n') };
        }
        await chat.setModel(args);
        return { content: `Model set to: ${chat.model.value ?? args}` };
      }

      case 'think': {
        if (!args) {
          return {
            content: `Current thinking level: ${chat.thinkingLevel.value}\nOptions: ${THINKING_LEVELS.join(', ')}`,
          };
        }
        const lvl = normalizeThinkingLevel(args);
        if (!lvl) {
          return {
            content: `Unknown thinking level "${args}". Valid: ${THINKING_LEVELS.join(', ')}`,
            isError: true,
          };
        }
        await chat.setThinking(lvl);
        return { content: `Thinking level set to: ${lvl}` };
      }

      case 'skills': {
        const skills = await chat.listSkills();
        return { content: formatNamedEntries('Skills', skills) };
      }

      case 'templates': {
        const tpls = await chat.getPromptTemplates();
        return { content: formatNamedEntries('Prompt templates', tpls) };
      }

      default:
        return { content: `Unknown command: /${command.name}`, isError: true };
    }
  } catch (e) {
    return { content: `Command failed: ${(e as Error).message}`, isError: true };
  }
}

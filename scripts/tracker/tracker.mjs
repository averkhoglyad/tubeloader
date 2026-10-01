#!/usr/bin/env node
//
// GitHub Issues tracker client. Replaces `gh` via plain REST + PAT.
// Run `node scripts/tracker/tracker.mjs help` for the command list.

import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const configuredPath = configFlag();
const configPath = configuredPath
  ? resolve(process.cwd(), configuredPath)
  : resolve(dirname(fileURLToPath(import.meta.url)), 'tracker.config.json');
const API = 'https://api.github.com';

function configFlag() {
  const index = process.argv.indexOf('--config');
  if (index === -1) return null;
  const value = process.argv[index + 1];
  if (!value) throw new Error('--config requires a path');
  process.argv.splice(index, 2);
  return value;
}

const config = JSON.parse(readFileSync(configPath, 'utf8'));

function resolveToken() {
  const names = Array.isArray(config.tokenEnv) ? config.tokenEnv : [config.tokenEnv];
  const namesLabel = names.join(', ');

  for (const name of names) {
    const value = process.env[name];
    if (value && value.trim()) return { name, value: value.trim() };
  }

  // The token lives in the Windows User scope, which an already-running IDE process did not
  // inherit. Fall back to reading it directly before declaring it missing.
  if (process.platform === 'win32') {
    try {
      for (const name of names) {
        const script = `[Environment]::GetEnvironmentVariable('${name}','User')`;
        const value = execFileSync('powershell.exe', ['-NoProfile', '-Command', script], {
          encoding: 'utf8',
        }).trim();
        if (value) return { name: `${name} (User scope)`, value };
      }
    } catch {
      // fall through to the error below
    }
  }

  throw new Error(
    `No token found. Set one of: ${namesLabel}.\n` +
      `This must be a classic PAT with the "repo" scope.`
  );
}

const token = resolveToken();

async function request(method, path, body) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token.value}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': config.apiVersion,
      'User-Agent': 'tubeloader-tracker',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const detail = payload ? JSON.stringify(payload) : text;
    throw new Error(`${method} ${path} -> ${response.status} ${detail}`);
  }
  return payload;
}

const repoPath = `/repos/${config.owner}/${config.repo}`;
const issuePath = (number) => `${repoPath}/issues/${number}`;

async function issueId(number) {
  const issue = await request('GET', issuePath(number));
  return issue.id;
}

function normalizeNumber(value) {
  const text = String(value).trim().replace(/^#/, '');
  if (!/^\d+$/.test(text)) throw new Error(`Not an issue number: ${value}`);
  return Number(text);
}

function parseArgs(argv) {
  const positional = [];
  const flags = {};
  const aliases = { '-t': '--title', '-b': '--body', '-l': '--label', '-a': '--assignee' };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = aliases[argv[i]] ?? argv[i];
    if (!arg.startsWith('--')) {
      positional.push(arg);
      continue;
    }
    const name = arg.slice(2);
    const next = argv[i + 1];
    if (next === undefined || (next.startsWith('--') && !aliases[next])) {
      flags[name] = true;
    } else {
      flags[name] = aliases[next] ?? next;
      i += 1;
    }
  }
  return { positional, flags };
}

function bodyFrom(flags) {
  if (flags['body-file']) return readFileSync(flags['body-file'], 'utf8');
  if (flags.body) return String(flags.body);
  return undefined;
}

function commentFrom(flags) {
  if (flags['comment-file']) return readFileSync(flags['comment-file'], 'utf8');
  if (flags.comment) return String(flags.comment);
  return undefined;
}

function splitList(value) {
  return String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function summarize(issue) {
  const labels = (issue.labels ?? []).map((label) => label.name).join(',');
  const assignees = (issue.assignees ?? []).map((user) => user.login).join(',');
  return [
    `#${issue.number}`,
    issue.state,
    labels ? `[${labels}]` : '',
    assignees ? `@{${assignees}}` : '',
    issue.title,
  ]
    .filter(Boolean)
    .join(' ');
}

function isPullRequest(issue) {
  return Boolean(issue.pull_request);
}

const commands = {};

commands.help = async () => {
  console.log(`Usage: node scripts/tracker/tracker.mjs <group> <command> [flags]

  check                                  verify token, repo and rate limit

  issue create  --title T [--body-file F | --body B] [--label L,L] [--assignee A,A] [--milestone N]
  issue view    N [--comments]
  issue list    [--state open|closed|all] [--label L,L] [--assignee A] [--json]
  issue edit    N [--title T] [--body-file F] [--add-label L,L] [--remove-label L,L]
  issue comment N --body-file F | --body B
  issue close   N [--comment-file F | --comment C] [--reason completed|not_planned]
  issue reopen  N

  label list

  block   N --by M       mark N blocked by M
  unblock N --by M
  blockers N             list N's blockers
  blocking N             list what N blocks

  sub add    CHILD --to PARENT
  sub list   PARENT
  sub remove PARENT --child CHILD
  parent     CHILD

  claim   N              assign N to yourself
  unclaim N
  resolve N --body-file F | --body B     comment + close
  frontier --map N | --label L          open, unassigned, unblocked issues

Bodies and comments: prefer --body-file / --comment-file. Multi-line markdown through a
shell argument breaks on quoting far more often than a file does.`);
};

commands.check = async () => {
  const viewer = await request('GET', '/user');
  const repo = await request('GET', repoPath);
  const rate = await request('GET', '/rate_limit');
  console.log(`token env   ${token.name}`);
  console.log(`identity   ${viewer.login}`);
  console.log(`repo       ${repo.full_name} (${repo.visibility}, issues=${repo.has_issues})`);
  console.log(`permission ${Object.entries(repo.permissions).filter(([, v]) => v).map(([k]) => k).join(',')}`);
  console.log(`rate       ${rate.resources.core.remaining}/${rate.resources.core.limit}`);
};

commands['issue create'] = async ({ flags }) => {
  const payload = { title: flags.title };
  const body = bodyFrom(flags);
  if (body !== undefined) payload.body = body;
  if (flags.label) payload.labels = splitList(flags.label);
  if (flags.assignee) payload.assignees = splitList(flags.assignee);
  if (flags.milestone) payload.milestone = Number(flags.milestone);
  if (!payload.title) throw new Error('issue create requires --title');

  const issue = await request('POST', `${repoPath}/issues`, payload);
  console.log(`${issue.html_url}`);
};

commands['issue view'] = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const issue = await request('GET', issuePath(number));
  console.log(summarize(issue));
  console.log(issue.body ?? '');
  if (flags.comments) {
    const comments = await request('GET', `${issuePath(number)}/comments?per_page=100`);
    for (const comment of comments) {
      console.log('\n---');
      console.log(`${comment.user.login} @ ${comment.created_at}`);
      console.log(comment.body);
    }
  }
};

commands['issue list'] = async ({ flags }) => {
  const params = new URLSearchParams({ per_page: '100' });
  params.set('state', flags.state ?? 'open');
  if (flags.label) params.set('labels', splitList(flags.label).join(','));
  if (flags.assignee) params.set('assignee', flags.assignee);
  if (flags.since) params.set('since', flags.since);

  const issues = (await request('GET', `${repoPath}/issues?${params}`)).filter((issue) => !isPullRequest(issue));
  if (flags.json) {
    console.log(JSON.stringify(issues, null, 2));
    return;
  }
  for (const issue of issues) console.log(summarize(issue));
};

commands['issue edit'] = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const payload = {};
  if (flags.title) payload.title = flags.title;
  const body = bodyFrom(flags);
  if (body !== undefined) payload.body = body;
  if (flags['add-label']) payload.labels = splitList(flags['add-label']);
  if (flags.state) payload.state = flags.state;
  if (flags.reason) payload.state_reason = flags.reason;

  if (flags['remove-label']) {
    for (const label of splitList(flags['remove-label'])) {
      await request('DELETE', `${issuePath(number)}/labels/${encodeURIComponent(label)}`);
    }
  }
  if (Object.keys(payload).length > 0) {
    const issue = await request('PATCH', issuePath(number), payload);
    console.log(summarize(issue));
  } else {
    console.log(`#${number} labels updated`);
  }
};

commands['issue comment'] = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const body = bodyFrom(flags);
  if (!body) throw new Error('issue comment requires --body-file or --body');
  const comment = await request('POST', `${issuePath(number)}/comments`, { body });
  console.log(comment.html_url);
};

commands['issue close'] = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const comment = commentFrom(flags);
  if (comment) await request('POST', `${issuePath(number)}/comments`, { body: comment });
  const issue = await request('PATCH', issuePath(number), {
    state: 'closed',
    state_reason: flags.reason ?? 'completed',
  });
  console.log(summarize(issue));
};

commands['issue reopen'] = async ({ positional }) => {
  const number = normalizeNumber(positional[0]);
  const issue = await request('PATCH', issuePath(number), { state: 'open' });
  console.log(summarize(issue));
};

commands['label list'] = async () => {
  const labels = await request('GET', `${repoPath}/labels?per_page=100`);
  for (const label of labels) console.log(`${label.name}\t#${label.color}\t${label.description ?? ''}`);
};

commands.block = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const blocker = normalizeNumber(flags.by);
  const id = await issueId(blocker);
  await request('POST', `${issuePath(number)}/dependencies/blocked_by`, { issue_id: id });
  console.log(`#${number} is now blocked by #${blocker}`);
};

commands.unblock = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const blocker = normalizeNumber(flags.by);
  const id = await issueId(blocker);
  await request('DELETE', `${issuePath(number)}/dependencies/blocked_by/${id}`);
  console.log(`#${number} is no longer blocked by #${blocker}`);
};

commands.blockers = async ({ positional }) => {
  const number = normalizeNumber(positional[0]);
  const issues = await request('GET', `${issuePath(number)}/dependencies/blocked_by?per_page=100`);
  for (const issue of issues) console.log(summarize(issue));
};

commands.blocking = async ({ positional }) => {
  const number = normalizeNumber(positional[0]);
  const issues = await request('GET', `${issuePath(number)}/dependencies/blocking?per_page=100`);
  for (const issue of issues) console.log(summarize(issue));
};

commands['sub add'] = async ({ positional, flags }) => {
  const child = normalizeNumber(positional[0]);
  const parent = normalizeNumber(flags.to);
  const id = await issueId(child);
  await request('POST', `${issuePath(parent)}/sub_issues`, { sub_issue_id: id, replace_parent: true });
  console.log(`#${child} is now a sub-issue of #${parent}`);
};

commands['sub list'] = async ({ positional }) => {
  const parent = normalizeNumber(positional[0]);
  const issues = await request('GET', `${issuePath(parent)}/sub_issues?per_page=100`);
  for (const issue of issues) console.log(summarize(issue));
};

commands['sub remove'] = async ({ positional, flags }) => {
  const parent = normalizeNumber(positional[0]);
  const child = normalizeNumber(flags.child);
  const id = await issueId(child);
  await request('DELETE', `${issuePath(parent)}/sub_issue`, { sub_issue_id: id });
  console.log(`#${child} is no longer a sub-issue of #${parent}`);
};

commands.parent = async ({ positional }) => {
  const number = normalizeNumber(positional[0]);
  const issue = await request('GET', `${issuePath(number)}/parent`);
  console.log(summarize(issue));
};

commands.claim = async ({ positional }) => {
  const number = normalizeNumber(positional[0]);
  const viewer = await request('GET', '/user');
  const issue = await request('POST', `${issuePath(number)}/assignees`, { assignees: [viewer.login] });
  console.log(summarize(issue));
};

commands.unclaim = async ({ positional }) => {
  const number = normalizeNumber(positional[0]);
  const viewer = await request('GET', '/user');
  await request('DELETE', `${issuePath(number)}/assignees`, { assignees: [viewer.login] });
  console.log(`#${number} unassigned`);
};

commands.resolve = async ({ positional, flags }) => {
  const number = normalizeNumber(positional[0]);
  const body = bodyFrom(flags);
  if (!body) throw new Error('resolve requires --body-file or --body');
  await request('POST', `${issuePath(number)}/comments`, { body });
  const issue = await request('PATCH', issuePath(number), { state: 'closed', state_reason: 'completed' });
  console.log(`resolved ${summarize(issue)}`);
};

async function scope({ flags }) {
  if (flags.map) {
    const parent = normalizeNumber(flags.map);
    return (await request('GET', `${issuePath(parent)}/sub_issues?per_page=100`)).map((issue) => issue.number);
  }
  const params = new URLSearchParams({ state: 'open', per_page: '100' });
  if (!flags.label) {
    throw new Error(
      'frontier requires --label L, or --map N. The default label is a flow rule, not a tool default.'
    );
  }
  params.set('labels', flags.label);
  const issues = await request('GET', `${repoPath}/issues?${params}`);
  return issues.filter((issue) => !isPullRequest(issue)).map((issue) => issue.number);
}

commands.frontier = async ({ flags }) => {
  const numbers = await scope({ flags });
  for (const number of numbers.sort((a, b) => a - b)) {
    const issue = await request('GET', issuePath(number));
    if (issue.state !== 'open') continue;
    if ((issue.assignees ?? []).length > 0) continue;
    if (issue.issue_dependencies_summary.blocked_by > 0) continue;
    console.log(summarize(issue));
  }
};

async function main() {
  const argv = process.argv.slice(2);
  if (argv.length === 0) {
    await commands.help({ positional: [], flags: {} });
    return;
  }
  const key = argv[0] === 'issue' || argv[0] === 'sub' || argv[0] === 'label' ? `${argv[0]} ${argv[1]}` : argv[0];
  const command = commands[key];
  if (!command) {
    await commands.help({ positional: [], flags: {} });
    process.exitCode = 1;
    return;
  }
  const rest = argv[0] === 'issue' || argv[0] === 'sub' || argv[0] === 'label' ? argv.slice(2) : argv.slice(1);
  await command(parseArgs(rest));
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exitCode = 1;
});

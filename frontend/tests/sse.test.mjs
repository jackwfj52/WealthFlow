import assert from 'node:assert/strict';
import test from 'node:test';
import { readServerEvents } from '../src/services/sse.ts';

test('SSE preserves Chinese UTF-8 across arbitrary byte boundaries and stops at final result', async () => {
  const bytes = new TextEncoder().encode('event: progress\r\ndata: {"label":"查询币安行情"}\r\n\r\n: heartbeat\n\nevent: result\ndata: {"reply":"完成"}\n\n');
  const stream = new ReadableStream({ start(controller) {
    for (const byte of bytes) controller.enqueue(new Uint8Array([byte]));
    controller.close();
  } });
  const events = [];
  await readServerEvents(stream, event => { events.push(event); if (event.event === 'result') return false; });
  assert.equal(events.length, 2);
  assert.equal(JSON.parse(events[0].data).label, '查询币安行情');
  assert.equal(JSON.parse(events[1].data).reply, '完成');
});

test('SSE handles multiple data lines and a final unterminated frame', async () => {
  const stream = new ReadableStream({ start(controller) {
    controller.enqueue(new TextEncoder().encode('event: progress\ndata: first\ndata: second'));
    controller.close();
  } });
  const events = [];
  await readServerEvents(stream, event => { events.push(event); });
  assert.equal(events[0].data, 'first\nsecond');
});

test('SSE propagates server error without waiting or replaying a request', async () => {
  let cancelled = false;
  const stream = new ReadableStream({ start(controller) {
    controller.enqueue(new TextEncoder().encode('event: error\ndata: {"message":"工具超时"}\n\n'));
  }, cancel() { cancelled = true; } });
  await assert.rejects(readServerEvents(stream, event => { throw new Error(JSON.parse(event.data).message); }), /工具超时/);
  assert.equal(cancelled, true);
});

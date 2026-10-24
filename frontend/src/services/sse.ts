export interface ServerEvent { event: string; data: string }

/** Incremental UTF-8/SSE decoding, including frames split at arbitrary byte boundaries. */
export async function readServerEvents(stream: ReadableStream<Uint8Array>, onEvent: (event: ServerEvent) => boolean | void) {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let stopped = false;
  const consume = () => {
    buffer = buffer.replace(/\r\n/g, '\n');
    let boundary: number;
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      let event = 'message';
      const data: string[] = [];
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
      }
      if (data.length && onEvent({ event, data: data.join('\n') }) === false) { stopped = true; return; }
    }
    if (buffer.length > 2_000_000) throw new Error('服务器事件超过大小限制');
  };
  try {
    while (!stopped) {
      const { value, done } = await reader.read();
      buffer += done ? decoder.decode() : decoder.decode(value, { stream: true });
      if (done && buffer.trim()) buffer += '\n\n';
      consume();
      if (done) break;
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

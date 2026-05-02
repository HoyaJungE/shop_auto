import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TaskQueue } from '../queue/taskQueue';

describe('TaskQueue', () => {
  let queue: TaskQueue<string>;

  beforeEach(() => {
    queue = new TaskQueue<string>(1);
  });

  it('작업을 순차적으로 처리한다', async () => {
    const order: number[] = [];

    const job1 = async () => { order.push(1); return 'a'; };
    const job2 = async () => { order.push(2); return 'b'; };
    const job3 = async () => { order.push(3); return 'c'; };

    await Promise.all([
      queue.enqueue('job1', job1),
      queue.enqueue('job2', job2),
      queue.enqueue('job3', job3),
    ]);

    expect(order).toEqual([1, 2, 3]);
  });

  it('작업 성공 결과를 반환한다', async () => {
    const result = await queue.enqueue('test', async () => 'hello');
    expect(result).toBe('hello');
  });

  it('작업 실패 시 Promise가 reject된다', async () => {
    await expect(
      queue.enqueue('fail', async () => { throw new Error('테스트 오류'); })
    ).rejects.toThrow('테스트 오류');
  });

  it('실패한 작업 이후에도 다음 작업을 계속 처리한다', async () => {
    const results: string[] = [];

    await queue.enqueue('ok1', async () => { results.push('ok1'); return ''; }).catch(() => {});
    await queue.enqueue('fail', async () => { throw new Error('fail'); }).catch(() => {});
    await queue.enqueue('ok2', async () => { results.push('ok2'); return ''; }).catch(() => {});

    expect(results).toContain('ok1');
    expect(results).toContain('ok2');
  });

  it('size와 activeCount가 정확하다', async () => {
    let resolveJob!: () => void;
    const blockingJob = () => new Promise<string>((resolve) => {
      resolveJob = () => resolve('done');
    });

    // 첫 번째 작업 시작 (블로킹)
    const p1 = queue.enqueue('blocking', blockingJob);
    queue.enqueue('pending1', async () => 'x');
    queue.enqueue('pending2', async () => 'x');

    // 약간 대기 후 상태 확인
    await new Promise(r => setTimeout(r, 10));
    expect(queue.activeCount).toBe(1);
    expect(queue.size).toBe(2);  // 대기 중 2개

    resolveJob();
    await p1;
  });

  it('concurrency=2이면 두 작업을 동시에 처리한다', async () => {
    const concurrentQueue = new TaskQueue<void>(2);
    const running: number[] = [];
    let maxConcurrent = 0;

    const makeJob = (id: number) => async () => {
      running.push(id);
      maxConcurrent = Math.max(maxConcurrent, running.length);
      await new Promise(r => setTimeout(r, 20));
      running.splice(running.indexOf(id), 1);
    };

    await Promise.all([
      concurrentQueue.enqueue('j1', makeJob(1)),
      concurrentQueue.enqueue('j2', makeJob(2)),
      concurrentQueue.enqueue('j3', makeJob(3)),
    ]);

    expect(maxConcurrent).toBe(2);
  });

  it('성공 이벤트를 올바르게 emit한다', async () => {
    const events: string[] = [];
    queue.on('enqueue',  (id) => events.push(`enqueue:${id}`));
    queue.on('start',    (id) => events.push(`start:${id}`));
    queue.on('complete', (id) => events.push(`complete:${id}`));

    await queue.enqueue('myTask', async () => 'done');

    expect(events).toContain('enqueue:myTask');
    expect(events).toContain('start:myTask');
    expect(events).toContain('complete:myTask');
  });

  it('실패 시 taskError 이벤트를 emit한다', async () => {
    const errors: string[] = [];
    queue.on('taskError', (id) => errors.push(id));

    await queue.enqueue('failTask', async () => { throw new Error('fail'); }).catch(() => {});

    expect(errors).toContain('failTask');
  });
});

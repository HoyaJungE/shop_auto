import { EventEmitter } from 'events';
import logger from '../logger';

type Job<T> = () => Promise<T>;

interface QueueItem<T> {
  id: string;
  job: Job<T>;
  resolve: (value: T) => void;
  reject: (reason: unknown) => void;
}

/**
 * 순차 처리 큐
 *
 * 브라우저 자동화는 동시에 여러 창을 띄우면 봇 감지 위험이 높아지고
 * 리소스도 많이 사용하므로 한 번에 하나씩 처리한다.
 *
 * 필요 시 concurrency 값을 높여서 병렬 처리도 가능하다.
 */
export class TaskQueue<T = unknown> extends EventEmitter {
  private queue: QueueItem<T>[] = [];
  private running = 0;
  private readonly concurrency: number;

  constructor(concurrency = 1) {
    super();
    this.concurrency = concurrency;
    // Node.js EventEmitter는 'error' 이벤트에 리스너가 없으면 예외를 던진다.
    // 기본 no-op 리스너를 등록해서 unhandled error를 방지한다.
    this.on('error', () => {});
  }

  enqueue(id: string, job: Job<T>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      this.queue.push({ id, job, resolve, reject });
      this.emit('enqueue', id);
      this.tick();
    });
  }

  get size(): number {
    return this.queue.length;
  }

  get activeCount(): number {
    return this.running;
  }

  private tick(): void {
    if (this.running >= this.concurrency || this.queue.length === 0) return;

    const item = this.queue.shift()!;
    this.running++;
    this.emit('start', item.id);
    logger.info(`[Queue] 작업 시작: ${item.id} (실행 중: ${this.running}, 대기: ${this.queue.length})`);

    item
      .job()
      .then((result) => {
        item.resolve(result);
        this.emit('complete', item.id, result);
        logger.info(`[Queue] 작업 완료: ${item.id}`);
      })
      .catch((err) => {
        item.reject(err);
        // 'taskError'로 명명 — 'error'는 Node.js EventEmitter 특수 이벤트라 리스너 없으면 예외 발생
        this.emit('taskError', item.id, err);
        logger.error(`[Queue] 작업 실패: ${item.id} — ${(err as Error).message}`);
      })
      .finally(() => {
        this.running--;
        this.tick();
      });
  }
}

// 전역 싱글톤 큐 (등록 작업용)
export const registerQueue = new TaskQueue(1);

// 크롤링 큐 (크롤링 작업용)
export const crawlQueue = new TaskQueue(1);

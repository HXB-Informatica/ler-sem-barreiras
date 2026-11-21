export class LsbException extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'LsbException';
  }
}

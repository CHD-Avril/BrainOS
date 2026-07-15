function continuation(value: number): boolean {
  return (value & 0xc0) === 0x80
}

function sequenceLength(value: number): number {
  if (value <= 0x7f) return 1
  if (value >= 0xc2 && value <= 0xdf) return 2
  if (value >= 0xe0 && value <= 0xef) return 3
  if (value >= 0xf0 && value <= 0xf4) return 4
  return 1
}

function decode(bytes: Uint8Array): string {
  let output = ''
  for (let index = 0; index < bytes.length;) {
    const first = bytes[index]!
    const length = sequenceLength(first)
    if (length === 1) {
      output += first <= 0x7f ? String.fromCharCode(first) : '\ufffd'
      index += 1
      continue
    }
    const part = bytes.slice(index, index + length)
    if (part.length !== length || part.slice(1).some(value => !continuation(value))) {
      output += '\ufffd'
      index += 1
      continue
    }
    let point = first & (0x7f >> length)
    for (let offset = 1; offset < length; offset += 1) point = (point << 6) | (part[offset]! & 0x3f)
    const minimum = length === 2 ? 0x80 : length === 3 ? 0x800 : 0x10000
    if (point < minimum || point > 0x10ffff || (point >= 0xd800 && point <= 0xdfff)) output += '\ufffd'
    else output += String.fromCodePoint(point)
    index += length
  }
  return output
}

export class Utf8StreamDecoder {
  private pending = new Uint8Array()

  push(chunk: Uint8Array): string {
    const bytes = new Uint8Array(this.pending.length + chunk.length)
    bytes.set(this.pending)
    bytes.set(chunk, this.pending.length)
    let completeEnd = bytes.length
    let start = bytes.length - 1
    while (start >= 0 && continuation(bytes[start]!)) start -= 1
    if (start >= 0 && sequenceLength(bytes[start]!) > bytes.length - start) completeEnd = start
    else if (start < 0) completeEnd = 0
    this.pending = bytes.slice(completeEnd)
    return decode(bytes.slice(0, completeEnd))
  }

  finish(): string {
    const output = decode(this.pending)
    this.pending = new Uint8Array()
    return output
  }
}

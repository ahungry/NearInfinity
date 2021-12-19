// Derived from logic in SavResourceEntry in NearInfinity project
const fs = require('fs')
const p = require('process')

const buf = fs.readFileSync('./test.sav')
const dv = new DataView(buf.buffer)
const header = buf.slice(0, 8).toString('ascii')

if (header !== 'SAV V1.0') {
  p.exit(1)
}

let offset = 8
const entries = []

function get_entry (buf, offset) {
  const fileNameLength = dv.getInt32(offset, 1) // little endian, tricky
  offset += 4

  const fileName = buf.slice(offset, offset + fileNameLength - 1).toString('ascii')
  offset += fileNameLength

  const uncompressedLength = dv.getInt32(offset, 1)
  offset += 4

  const compressedLength = dv.getInt32(offset, 1)
  offset += 4

  const cdata = buf.slice(offset, compressedLength)
  offset += compressedLength

  const entry = ''

  return {
    cdata,
    compressedLength,
    fileName,
    offset,
    uncompressedLength,
  }
}

while (offset < buf.length) {
  const entry = get_entry(buf, offset)

  offset = entry.offset
  entries.push(entry)
  console.log(entry.fileName)
}
console.log('end offset: ', offset)

console.log(entries)

// console.log(header)
// console.log(entries)

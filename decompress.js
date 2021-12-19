// Derived from logic in SavResourceEntry in NearInfinity project
const fs = require('fs')
const p = require('process')
const zlib = require('zlib')

const buf = fs.readFileSync('./test.sav')
const header = buf.slice(0, 8).toString('ascii')

if (header !== 'SAV V1.0') {
  p.exit(1)
}

let offset = 8
const entries = {}

function get_entry (buf, offset) {
  const dv = new DataView(buf.buffer)
  const fileNameLength = dv.getInt32(offset, 1) // little endian, tricky
  offset += 4

  const fileName = buf.slice(offset, offset + fileNameLength - 1).toString('ascii')
  offset += fileNameLength

  // console.log({dv, offset})
  const uncompressedLength = dv.getInt32(offset, 1)
  offset += 4

  const compressedLength = dv.getInt32(offset, 1)
  offset += 4

  const cdata = buf.slice(offset, offset + compressedLength)
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
  entries[entry.fileName] = entry
}

// console.log('end offset: ', offset)
// console.log(entries['THBAG05.sto'])

// console.log(header)
// console.log(entries)

function show_bag(key) {
  const x = entries[key].cdata
  const z = zlib.inflateSync(x)

  // First 8 of a store = type or something
  const zheader = z.slice(0, 8).toString('ascii')

  // const fh = fs.openSync('test.sto', 'w')
  // fs.writeSync(fh, z, 0, z.length, 0)

  // Item gets 28 bytes - store offset is at 147
  // Ending padding is offset is 144 + 4 extra, hmm..
  console.log(z.slice(0x90 + 12, 0x90 + 12 + 28).toString('ascii'))
  console.log(z.slice(156, 156 + 28).toString('ascii'))

  for (let i = 156; i < z.length - 148; i += 28) {
    console.log(z.slice(i, i+28).toString('ascii'))
    // console.log(z.slice(i, i+28))
  }
}

show_bag('THBAG05.sto')
show_bag('THBAG03.sto')
show_bag('THBAG01.sto')

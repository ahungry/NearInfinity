// Derived from logic in SavResourceEntry in NearInfinity project
const fs = require('fs')
const p = require('process')
const zlib = require('zlib')

/**
 * The basic data structure used in the .sav file - 8 bytes at start identify
 * the type+version info, and the rest of the data is Entry type.
 */
class Entry {
  /**
   * Entry begins at an offset in buffer, it has the following format
   *
   *   fileNameLen[4 bytes, little-endian]
   *   fileName[X bytes]
   *   uncompLen[4 bytes, LE]
   *   compLen[4 bytes, LE]
   *   cdata[Y bytes]
   *
   * Therefore it is always 12 bytes + X bytes (fileNameSize) + Y bytes (cdata size)
   *
   */
  static fromSavBuf (buf, offset) {
    const initial_offset = offset
    const dv = new DataView(buf.buffer)
    const fileNameLen = dv.getInt32(offset, 1) // little endian, tricky

    // Just for checking data sanity, save original binary info
    const sanityHeader = buf.slice(initial_offset, initial_offset + fileNameLen + 12)

    offset += 4

    const fileName = buf.slice(offset, offset + fileNameLen - 1).toString('ascii')
    offset += fileNameLen

    // console.log({dv, offset})
    const uncompLen = dv.getInt32(offset, 1)
    offset += 4

    const compLen = dv.getInt32(offset, 1)
    offset += 4

    const cdata = buf.slice(offset, offset + compLen)
    offset += compLen

    const entry = ''

    return new Entry({
      cdata,
      compLen,
      fileName,
      fileNameLen,
      uncompLen,
      sanityHeader,
    })
  }

  constructor ({ cdata, fileNameLen, fileName, compLen, uncompLen, sanityHeader }) {
    this.cdata = cdata
    this.fileNameLen = fileNameLen
    this.fileName = fileName
    this.compLen = compLen
    this.uncompLen = uncompLen
    this.sanityHeader = sanityHeader
  }

  // 4 fileNameLen, 4 cdataLen, 4 uncompData
  SIZE_BUFFERS = 12

  // The size doesn't include uncompLen because that's just a check after inflate
  getSize () {
    return this.fileNameLen + this.compLen + this.SIZE_BUFFERS
  }

  // The cdata is actually an object like a .STO (store) - it could
  // write to disk as it's own data file for the IE game.
  getData () {
    const unpacked = zlib.inflateSync(this.cdata)

    if (unpacked.length !== this.uncompLen) {
      throw new Error('Unpack on Entry failed - mismatched sizes!')
    }

    return unpacked
  }

  setData (data) {
    const packed = zlib.deflateSync(data, { level: 9 })
    this.uncompLen = data.length
    this.compLen = packed.length
    this.cdata = packed

    return this
  }

  toCompSavBuffer () {
    const len = this.getSize()
    const buf = new Buffer(len)
    const dv = new DataView(buf.buffer)

    // Set the size headers - first fileNameLen
    dv.setInt32(0, this.fileNameLen, 1) // offset, value, little-endian

    const fileNameBuf = Buffer.from(this.fileName)
    fileNameBuf.copy(buf, 4)

    // Next, set the uncompLen
    dv.setInt32(4 + this.fileNameLen, this.uncompLen, 1) // offset, value, little-endian

    // Then the compLen
    dv.setInt32(8 + this.fileNameLen, this.compLen, 1) // offset, value, little-endian

    const cdataBuf = Buffer.from(this.cdata)
    cdataBuf.copy(buf, 12 + this.fileNameLen)

    return buf
  }
}

/**
 * Collection to manage entries
 */
class Entries {
  static fromSavBuf(buf) {
    const header = buf.slice(0, 8).toString('ascii')

    if (header !== 'SAV V1.0') {
      throw new Error('Invalid .SAV file format - first 8 bytes were not "SAV V1.0".')
    }

    let offset = 8
    const x = new Entries(buf)

    // Build all the entries - equivalent of the .sav file
    while (offset < buf.length) {
      // const entry = get_entry(buf, offset)
      const entry = Entry.fromSavBuf(buf, offset)

      offset += entry.getSize()

      x.add(entry)
    }

    return x
  }

  constructor (originalBuf) {
    this.originalBuf = originalBuf
    this.xs = []
  }

  add (x) {
    this.xs.push(x)
  }

  getByName (s) {
    for (let i = 0; i < this.xs.length; i++) {
      if (this.xs[i].fileName === s) return this.xs[i]
    }
  }

  /**
   * Re-create a proper .sav file from the split entries in this collection.
   * Involves adding the header, and then re-combining the individual
   * components.
   */
  toSavBuf () {
    const buffers = []
    let byteLen = 8 // Initial 8 for file info header

    // Make each entry a compressed buffer, get total size
    for (let i = 0; i < this.xs.length; i++) {
      const entry = this.xs[i]
      const buf = entry.toCompSavBuffer()
      buffers.push(buf)
      byteLen += buf.buffer.byteLength
    }

    const savBuf = new Buffer(byteLen)
    this.originalBuf.slice(0, 8).copy(savBuf, 0)

    let offset = 8

    for (let i = 0; i < buffers.length; i++) {
      buffers[i].copy(savBuf, offset)
      offset += buffers[i].buffer.byteLength
    }

    return savBuf
  }
}

/**
 * Given an entry that is of .STO (store) type, sort all the
 * values that are in the data portion and re-pack.
 */
function sort_bag (entry) {
  const z = entry.getData()

  // First 8 of a store = type or something
  // const zheader = z.slice(0, 8).toString('ascii')

  // Item gets 28 bytes - store offset is at 147
  // Ending padding is offset is 144 + 4 extra, hmm..
  console.log(z.slice(0x90 + 12, 0x90 + 12 + 28).toString('ascii'))
  console.log(z.slice(156, 156 + 28).toString('ascii'))

  const offsetStart = 156
  const offsetEnd = 148
  const itemSize = 28
  const items = []

  for (let i = offsetStart; i < z.length - offsetEnd; i += itemSize) {
    const item = z.slice(i, i + itemSize)

    items.push({ bytes: item, name: item.toString('ascii') })
  }

  const sorted = items.sort((a, b) => a.bytes.slice(0, 8) > b.bytes.slice(0, 8) ? 1 : -1)

  console.log(sorted)

  const sortedEntry = Buffer.from(z)
  console.log(sortedEntry)

  // Add values from our sorted in here
  for (let i = offsetStart, c = 0; i < z.length - offsetEnd; i += itemSize, c++) {
    const item = sorted[c] // grab the item from this iteration
    const bytes = item.bytes // should be 28 here

    for (let x = 0; x < 28; x++) {
      sortedEntry[i + x] = bytes[x]
    }
  }

  entry.setData(sortedEntry)

  return sortedEntry
}

const buf = fs.readFileSync('./test.sav')
const entries = Entries.fromSavBuf(buf)

const savFile = entries.toSavBuf()

const xxx = fs.openSync('repack.sav', 'w')
fs.writeSync(xxx, savFile, 0, savFile.length, 0)
fs.closeSync(xxx)
p.exit()

const x = entries.getByName('THBAG05.sto')

const newBuf = x.toCompSavBuffer()

console.log(newBuf.buffer.byteLength)
p.exit()
const sortedBag = sort_bag(x)

const fhx = fs.openSync('bag.sto', 'w')
fs.writeSync(fhx, sortedBag, 0, sortedBag.length, 0)
fs.closeSync(fhx)


p.exit()

const bagInfo = entries['THBAG05.sto']
const newCdata = zlib.deflateSync(sortedBag.sortedEntry, { level: 9 })
console.log(bagInfo)
console.log(newCdata.length)

console.log(bagInfo.cdata, newCdata)

for (let i = 0; i < bagInfo.cdata; i++) {
  if (bagInfo[i] !== newCdata[i]) {
    console.log('difference/mismatch at: ', i)
    p.exit()
  }
}
console.log('they are identical...')

// console.log(newCdata)
// console.log(bagInfo.cdata)


// Error with 'unknown compression method' - could be mismatch between labelled compressed size
// and the actual encoding

// BEGIN testing of compression levels -- Level 9 will provide the identical level of compression that the IE is using
// const m = entries['THBAG05.sto']
// const x = entries['THBAG05.sto'].cdata
// const z = zlib.inflateSync(x)
// const y = zlib.deflateSync(z, { level: 9 })
// console.log({ m,x,y,z, xlen: x.length, ylen: y.length, zlen: z.length })
// END testing of compression levels

// Try to add newCdata in place of old
// TODO: Likely will need to update this in place, but due to compression alteration
// after writing this cdata, update the cdata compressed length value, and then
// ensure the original buf data afterwards is put in the proper place (shift diff)
function updateCdata (entry, newCdata) {
  // console.log(entry)
  for (let i = 0; i < entry.compressedLength; i++) {
    buf[entry.cdata_begin_offset + i] = newCdata[i] // || 0
    // console.log({
    //   buf: buf[entry.cdata_begin_offset + i],
    //   new: newCdata[i]
    // })
  }
}

updateCdata(bagInfo, newCdata)

// Well, it produces a .sav the BG game can open, but NI will crash on.
// Also, it unfortunately doesn't seem to have altered the order of bag contents at all...

// Also, the size of deflate vs compressedLengths do not match up

const fh = fs.openSync('hax.sav', 'w')
fs.writeSync(fh, buf, 0, buf.length, 0)
fs.closeSync(fh)

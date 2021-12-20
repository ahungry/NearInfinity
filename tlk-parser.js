// https://gibberlings3.github.io/iesdp/file_formats/ie_formats/tlk_v1.htm

const fs = require('fs')
const p = require('process')

// Given a buffer with a nul terminated word, read up until first null byte
function bufToStringNul (buf) {
  let i = 0
  for (; i < buf.length; i++) {
    if (buf[i] === 0) break
  }

  return buf.slice(0, i).toString('ascii')
}

const buf = fs.readFileSync('dialog.tlk')
const dv = new DataView(buf.buffer)

console.log(dv)

const sigV = buf.slice(0, 8).toString('ascii')
const strrefLen = dv.getUint32(10, 1)
const stringsOffset = dv.getUint32(14, 1)

console.log({ sigV, strrefLen, myOffset: 26 * strrefLen, stringsOffset })

function read_item (buf, offset) {
  const dv = new DataView(buf.buffer)
  const bitField = dv.getUint16(0, 1)
  const resref = buf.slice(offset + 2, offset + 2 + 8)
  const stringStart = dv.getUint32(offset + 18, 1)
  const stringLen = dv.getUint32(offset + 22, 1)

  const stringContent = buf.slice(stringStart + stringsOffset, stringStart + stringsOffset + stringLen)

  const prettyName = bufToStringNul(resref)
  if (prettyName) {
    console.log({prettyName, x: stringContent.toString('ascii')})
  }


}

let offset = 18

for (let i = 0; i < strrefLen; i++) {
  read_item(buf, offset) // Each TLK entry is 26 bytes

  offset = 18 + (i * 26)

  if (offset > stringsOffset) {
    throw new Error('Need to drop out earlier...')
  }
}

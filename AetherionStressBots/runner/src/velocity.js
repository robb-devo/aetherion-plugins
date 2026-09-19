import crypto from 'node:crypto'

/**
 * Answer Paper's velocity:player_info login plugin request so bots can
 * connect straight to the backend (127.0.0.1:25567) without going through Velocity.
 * Secret must match proxies.velocity.secret / forwarding.secret.
 */
export function attachVelocityForwarding(bot, secret, usernameOverride) {
  if (!secret) {
    throw new Error('velocity forwarding secret missing')
  }
  const secretBuf = Buffer.from(String(secret), 'utf8')

  const handle = (packet) => {
    if (packet.channel !== 'velocity:player_info') return

    let requested = 1
    if (packet.data && packet.data.length >= 1) {
      requested = packet.data.readUInt8(0)
    }

    // 1.21 / >=1.19.3: prefer MODERN_LAZY_SESSION (4), else DEFAULT (1)
    const version = requested >= 4 ? 4 : 1
    const username = usernameOverride || bot.username
    if (!username) {
      throw new Error('velocity forwarding: username missing')
    }
    const uuid = offlinePlayerUuid(username)
    const address = '127.0.0.1'

    const payload = buildForwardingPayload(version, address, uuid, username)
    const signature = crypto.createHmac('sha256', secretBuf).update(payload).digest()
    const data = Buffer.concat([signature, payload])

    bot._client.write('login_plugin_response', {
      messageId: packet.messageId,
      data
    })
  }

  // minecraft-protocol registers a default handler that replies "not understood"
  // and races us — wipe it so only our Velocity HMAC response is sent.
  bot._client.removeAllListeners('login_plugin_request')
  bot._client.on('login_plugin_request', handle)
}

export function offlinePlayerUuid(name) {
  const hash = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest()
  hash[6] = (hash[6] & 0x0f) | 0x30
  hash[8] = (hash[8] & 0x3f) | 0x80
  const hex = hash.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function buildForwardingPayload(version, address, uuid, username) {
  const chunks = []
  chunks.push(writeVarInt(version))
  chunks.push(writeString(address))
  chunks.push(writeUuid(uuid))
  chunks.push(writeString(username))
  chunks.push(writeVarInt(0)) // empty properties
  return Buffer.concat(chunks)
}

function writeVarInt(value) {
  const out = []
  let v = value >>> 0
  while (true) {
    if ((v & ~0x7f) === 0) {
      out.push(v)
      break
    }
    out.push((v & 0x7f) | 0x80)
    v >>>= 7
  }
  return Buffer.from(out)
}

function writeString(str) {
  const utf8 = Buffer.from(str, 'utf8')
  return Buffer.concat([writeVarInt(utf8.length), utf8])
}

function writeUuid(uuid) {
  const hex = uuid.replace(/-/g, '')
  const buf = Buffer.alloc(16)
  buf.writeBigUInt64BE(BigInt(`0x${hex.slice(0, 16)}`), 0)
  buf.writeBigUInt64BE(BigInt(`0x${hex.slice(16, 32)}`), 8)
  return buf
}

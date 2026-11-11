package app.familyjourney.core.sync

/** 차 화면에 보일 연결 코드: QR 내용과 짧은 코드(QR을 못 찍을 때), 만료 시각 */
data class Pairing(val qr: PairingQr, val shortCode: String, val expiresAtEpochMs: Long) {
    /** 화면용 "K7QM-4RXP-2WDN" */
    val shortCodeDisplay: String get() = ShortCode.display(shortCode)
    override fun toString(): String = "Pairing(qr=$qr, shortCode=***, expiresAt=$expiresAtEpochMs)"
}

/** 화면에 보일 연결된 기기(이름은 가족 열쇠로 푼 것). 풀 수 없으면 "가족 기기" */
data class LinkedDevice(val deviceId: String, val name: String, val roles: Set<DeviceRole>, val lastSeenDate: String?)

/** 폰이 보는 우리 차 진행. [location]은 위치 권한이 있고 차가 위치 보내기를 켰을 때만 */
data class CarStatusView(val status: CarStatus, val location: LocationGrid?, val receivedAtEpochMs: Long)

/**
 * 차 쪽 연결 일(architecture.md 19-3, 주차 중 화면에서만 부른다). 화면은 feature:link가 만든다.
 */
class CarLink(private val client: SyncClient, private val secrets: SyncSecrets) {

    /** 이 차의 연결 정보(없으면 아직 가족을 만들지 않음) */
    suspend fun credentials(): LinkCredentials? = secrets.load()?.takeIf { it.isCar }

    /** 가족 만들기(처음 한 번): 가족 열쇠·기기 키를 이 차에서 만들고, 서버에는 기기 키 해시만 보낸다 */
    suspend fun createFamily(carName: String = "우리 가족 차"): SyncResult<LinkCredentials> {
        credentials()?.let { return SyncResult.Ok(it) }
        val familyKey = FamilyCipher.newFamilyKey()
        val deviceKey = Base64Url.random(32)
        // 가족 ID는 서버가 정하므로 차 이름 덩어리는 임시 ID("pending")로 잠근다. 차 이름은 기기 목록에 보이지 않는다
        val placeholder = FamilyCipher(familyKey, "pending").seal(carName, BlobKind.NAME)
        return when (val r = client.createFamily(Base64Url.sha256(deviceKey), placeholder)) {
            is SyncResult.Ok -> {
                val (familyId, deviceId) = r.value
                val creds = LinkCredentials(familyId, deviceId, deviceKey, familyKey, isCar = true, deviceName = carName)
                secrets.save(creds)
                SyncResult.Ok(creds)
            }
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }

    /** 새 연결 QR + 짧은 코드(5분, 한 번만). 이전 코드는 서버에서 버려진다 */
    suspend fun newPairing(creds: LinkCredentials): SyncResult<Pairing> {
        val qr = PairingQr.newFor(creds.familyId, creds.familyKey)
        val short = ShortCode.generate()
        val wrapped = ShortCode.wrapFamilyKey(short, creds.familyKey)
        return when (val r = client.openPairing(creds.authorization, Base64Url.sha256(qr.code), Base64Url.sha256(short), wrapped)) {
            is SyncResult.Ok -> SyncResult.Ok(Pairing(qr, short, r.value))
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }

    suspend fun pending(creds: LinkCredentials): SyncResult<List<LinkedDevice>> = names(creds, client.pendingDevices(creds.authorization))

    suspend fun devices(creds: LinkCredentials): SyncResult<List<LinkedDevice>> = names(creds, client.devices(creds.authorization))

    /** 보호자가 차 화면에서 [연결]을 눌렀을 때만 부른다(버튼 탭만 동의) */
    suspend fun approve(creds: LinkCredentials, deviceId: String, roles: Set<DeviceRole>): SyncResult<Unit> =
        client.approve(creds.authorization, deviceId, roles)

    suspend fun remove(creds: LinkCredentials, deviceId: String): SyncResult<Unit> = client.removeDevice(creds.authorization, deviceId)

    /** 모두 끊고 지우기: 서버의 가족 기록 전체와 이 차의 연결 정보를 지운다 */
    suspend fun wipe(creds: LinkCredentials): SyncResult<Unit> {
        val r = client.wipeFamily(creds.authorization)
        if (r is SyncResult.Ok) secrets.clear()
        return r
    }

    private fun names(creds: LinkCredentials, r: SyncResult<List<RemoteDevice>>): SyncResult<List<LinkedDevice>> {
        val cipher = creds.cipher()
        return when (r) {
            is SyncResult.Ok -> SyncResult.Ok(r.value.map { LinkedDevice(it.deviceId, cipher.open(it.nameBlob, BlobKind.NAME) ?: "가족 기기", it.roles, it.lastSeenDate) })
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }
}

/** 폰 쪽 연결 일. 화면은 phone/(앱 개발 세션)이 만든다. 이 폰의 이름은 [credentials]의 deviceName */
class PhoneLink(private val client: SyncClient, private val secrets: SyncSecrets, private val nowEpochMs: () -> Long = System::currentTimeMillis) {

    suspend fun credentials(): LinkCredentials? = secrets.load()?.takeIf { !it.isCar }

    /**
     * QR을 찍은 뒤: 기기 키를 이 폰에서 만들고(서버에는 해시만), 이름은 가족 열쇠로 잠가 보낸다.
     * 성공하면 연결 정보를 저장하고 "승인 대기"가 된다 → [status]로 차 승인을 기다린다.
     */
    suspend fun claim(qr: PairingQr, phoneName: String): SyncResult<LinkCredentials> {
        val deviceKey = Base64Url.random(32)
        val shown = cleanName(phoneName)
        val name = FamilyCipher(qr.familyKey, qr.familyId).seal(shown, BlobKind.NAME)
        return when (val r = client.claim(qr.familyId, qr.code, Base64Url.sha256(deviceKey), name)) {
            is SyncResult.Ok -> {
                val creds = LinkCredentials(qr.familyId, r.value, deviceKey, qr.familyKey, isCar = false, deviceName = shown)
                secrets.save(creds)
                SyncResult.Ok(creds)
            }
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }

    /**
     * 짧은 코드로 연결(QR을 못 찍을 때, 에뮬레이터 시연). [input]은 사람이 친 그대로("k7qm-4rxp-2wdn"도 됨).
     * 맞지 않는 모양이면 Failed(0, "bad_code"). 감싼 가족 열쇠를 코드로 풀고, 이름을 가족 열쇠로 다시 잠가 올린다.
     */
    suspend fun claimShort(input: String, phoneName: String): SyncResult<LinkCredentials> {
        val code = ShortCode.normalize(input) ?: return SyncResult.Failed(0, "bad_code")
        val deviceKey = Base64Url.random(32)
        val shown = cleanName(phoneName)
        return when (val r = client.claimShort(code, Base64Url.sha256(deviceKey), ShortCode.sealPlaceholder(code))) {
            is SyncResult.Ok -> {
                val familyKey = ShortCode.unwrapFamilyKey(code, r.value.wrappedKey) ?: return SyncResult.Failed(0, "bad_key")
                val creds = LinkCredentials(r.value.familyId, r.value.deviceId, deviceKey, familyKey, isCar = false, deviceName = shown)
                secrets.save(creds)
                client.setName(creds.authorization, creds.cipher().seal(shown, BlobKind.NAME)) // 실패해도 연결은 됨(차에는 "가족 폰"으로 보임)
                SyncResult.Ok(creds)
            }
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }

    /** QR 글자(ggf1.…) 또는 짧은 코드, 어느 쪽이든 받는다. 둘 다 아니면 Failed(0, "bad_code") */
    suspend fun claimText(text: String, phoneName: String): SyncResult<LinkCredentials> =
        PairingQr.parse(text)?.let { claim(it, phoneName) } ?: claimShort(text, phoneName)

    /** 이 폰의 표시 이름 바꾸기(저장 + 서버) */
    suspend fun rename(creds: LinkCredentials, phoneName: String): SyncResult<LinkCredentials> {
        val renamed = creds.copy(deviceName = cleanName(phoneName))
        return when (val r = client.setName(creds.authorization, creds.cipher().seal(renamed.deviceName, BlobKind.NAME))) {
            is SyncResult.Ok -> { secrets.save(renamed); SyncResult.Ok(renamed) }
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }

    private fun cleanName(name: String) = name.trim().take(12).ifEmpty { "가족 폰" }

    /** 승인됐나, 무엇을 할 수 있나 */
    suspend fun status(creds: LinkCredentials): SyncResult<MyStatus> = client.me(creds.authorization)

    /** 이 폰 연결 끊기(서버에서 바로 지움 + 이 폰의 열쇠 지움) */
    suspend fun leave(creds: LinkCredentials): SyncResult<Unit> {
        val r = client.removeDevice(creds.authorization, creds.deviceId)
        if (r is SyncResult.Ok || (r is SyncResult.Failed && r.status in setOf(401, 404))) secrets.clear()
        return r
    }

    /** 우리 차 진행(없으면 null: 여행 중이 아니거나 끝남). 5초마다 불러도 된다 */
    suspend fun carStatus(creds: LinkCredentials): SyncResult<CarStatusView?> {
        val cipher = creds.cipher()
        return when (val r = client.getStatus(creds.authorization)) {
            is SyncResult.Ok -> SyncResult.Ok(r.value?.let { s ->
                CarStatusCodec.open(s.blob, cipher)?.let { CarStatusView(it, s.locationBlob?.let { l -> CarStatusCodec.openLocation(l, cipher) }, nowEpochMs()) }
            })
            is SyncResult.Conflict -> SyncResult.Failed(409, "conflict")
            is SyncResult.Failed -> r
            SyncResult.Offline -> SyncResult.Offline
        }
    }
}

/** 차가 진행 상태를 올리는 일(feature:link의 CarStatusPublisher가 5초마다 부른다) */
class CarStatusSender(private val client: SyncClient) {
    suspend fun send(creds: LinkCredentials, status: CarStatus, location: LocationGrid?): SyncResult<Unit> {
        val cipher = creds.cipher()
        return client.putStatus(creds.authorization, CarStatusCodec.seal(status, cipher), location?.let { CarStatusCodec.sealLocation(it, cipher) }, null)
    }

    suspend fun clear(creds: LinkCredentials): SyncResult<Unit> = client.clearStatus(creds.authorization)
}

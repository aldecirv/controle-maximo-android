package com.controlemaximo.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.util.UUID

class InventoryDb(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE produtos (
                id TEXT PRIMARY KEY, nome TEXT NOT NULL, categoria TEXT NOT NULL,
                unidade_medida TEXT NOT NULL, custo REAL NOT NULL, preco_venda REAL NOT NULL,
                fornecedor TEXT NOT NULL, criado_em INTEGER NOT NULL
            )""".trimIndent())
        db.execSQL("""
            CREATE TABLE instituicoes (
                id TEXT PRIMARY KEY, nome TEXT NOT NULL, contato TEXT, telefone TEXT
            )""".trimIndent())
        db.execSQL("""
            CREATE TABLE lotes (
                id TEXT PRIMARY KEY, produto_id TEXT NOT NULL REFERENCES produtos(id) ON DELETE CASCADE,
                codigo_lote TEXT NOT NULL, data_fabricacao TEXT NOT NULL, data_validade TEXT NOT NULL,
                quantidade_inicial REAL NOT NULL, quantidade_disponivel REAL NOT NULL,
                quantidade_reservada REAL NOT NULL DEFAULT 0, desconto_percentual REAL NOT NULL DEFAULT 30,
                status TEXT NOT NULL DEFAULT 'ativo', criado_em INTEGER NOT NULL
            )""".trimIndent())
        db.execSQL("""
            CREATE TABLE movimentacoes (
                id TEXT PRIMARY KEY, lote_id TEXT NOT NULL REFERENCES lotes(id), tipo TEXT NOT NULL,
                quantidade REAL NOT NULL, valor_recebido REAL, forma_pagamento TEXT,
                instituicao_id TEXT REFERENCES instituicoes(id), motivo TEXT, observacao TEXT,
                criado_em INTEGER NOT NULL
            )""".trimIndent())
        db.execSQL("""
            CREATE TABLE reservas (
                id TEXT PRIMARY KEY, codigo TEXT UNIQUE NOT NULL, lote_id TEXT NOT NULL REFERENCES lotes(id),
                quantidade REAL NOT NULL, nome_cliente TEXT NOT NULL, telefone_cliente TEXT NOT NULL,
                criado_em INTEGER NOT NULL, expira_em INTEGER NOT NULL, status TEXT NOT NULL
            )""".trimIndent())
        db.execSQL("""
            CREATE TABLE configuracoes (
                id TEXT PRIMARY KEY, nome_loja TEXT NOT NULL, dias_atencao INTEGER NOT NULL,
                dias_urgente INTEGER NOT NULL, dias_vitrine INTEGER NOT NULL, desconto_padrao REAL NOT NULL
            )""".trimIndent())
        createMovementTriggers(db)
        insertDefaultSettings(db)
        seedDemo(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun createMovementTriggers(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_mov_no_update BEFORE UPDATE ON movimentacoes
            BEGIN SELECT RAISE(ABORT, 'Movimentações são imutáveis (append-only).'); END""".trimIndent())
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_mov_no_delete BEFORE DELETE ON movimentacoes
            BEGIN SELECT RAISE(ABORT, 'Movimentações são imutáveis (append-only).'); END""".trimIndent())
    }

    private fun insertDefaultSettings(db: SQLiteDatabase) {
        db.insertWithOnConflict(
            "configuracoes", null,
            ContentValues().apply {
                put("id", "default")
                put("nome_loja", "Mercado Controle Máximo")
                put("dias_atencao", 7)
                put("dias_urgente", 2)
                put("dias_vitrine", 5)
                put("desconto_padrao", 30.0)
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun settings(): Settings {
        readableDatabase.query("configuracoes", null, "id=?", arrayOf("default"), null, null, null).use { c ->
            if (c.moveToFirst()) return Settings(
                storeName = c.getString(c.getColumnIndexOrThrow("nome_loja")),
                attentionDays = c.getInt(c.getColumnIndexOrThrow("dias_atencao")),
                urgentDays = c.getInt(c.getColumnIndexOrThrow("dias_urgente")),
                showcaseDays = c.getInt(c.getColumnIndexOrThrow("dias_vitrine")),
                defaultDiscount = c.getDouble(c.getColumnIndexOrThrow("desconto_padrao"))
            )
        }
        return Settings()
    }

    fun saveSettings(settings: Settings) {
        require(settings.attentionDays > settings.urgentDays) { "Dias de atenção deve ser maior que os dias de urgência." }
        require(settings.urgentDays >= 1 && settings.showcaseDays >= 1) { "Os prazos devem ser maiores que zero." }
        require(settings.defaultDiscount in 0.0..100.0) { "Desconto deve estar entre 0 e 100%." }
        writableDatabase.insertWithOnConflict(
            "configuracoes", null,
            ContentValues().apply {
                put("id", "default")
                put("nome_loja", settings.storeName.ifBlank { "Minha loja" })
                put("dias_atencao", settings.attentionDays)
                put("dias_urgente", settings.urgentDays)
                put("dias_vitrine", settings.showcaseDays)
                put("desconto_padrao", settings.defaultDiscount)
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun products(): List<Product> = readableDatabase.query("produtos", null, null, null, null, null, "nome COLLATE NOCASE").use { c ->
        buildList { while (c.moveToNext()) add(c.toProduct()) }
    }

    private fun Cursor.toProduct() = Product(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("nome")),
        category = getString(getColumnIndexOrThrow("categoria")),
        unit = getString(getColumnIndexOrThrow("unidade_medida")),
        cost = getDouble(getColumnIndexOrThrow("custo")),
        price = getDouble(getColumnIndexOrThrow("preco_venda")),
        supplier = getString(getColumnIndexOrThrow("fornecedor"))
    )

    fun lots(): List<Lot> {
        val sql = """
            SELECT l.*, p.nome AS produto_nome, p.categoria, p.unidade_medida,
                   p.custo, p.preco_venda, p.fornecedor
            FROM lotes l JOIN produtos p ON p.id = l.produto_id
            ORDER BY l.data_validade ASC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add(c.toLot()) }
        }
    }

    fun lot(lotId: String): Lot? {
        val sql = """
            SELECT l.*, p.nome AS produto_nome, p.categoria, p.unidade_medida,
                   p.custo, p.preco_venda, p.fornecedor
            FROM lotes l JOIN produtos p ON p.id = l.produto_id
            WHERE l.id = ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(lotId)).use { c ->
            if (c.moveToFirst()) c.toLot() else null
        }
    }

    private fun Cursor.toLot() = Lot(
        id = getString(getColumnIndexOrThrow("id")),
        productId = getString(getColumnIndexOrThrow("produto_id")),
        code = getString(getColumnIndexOrThrow("codigo_lote")),
        manufactureDate = getString(getColumnIndexOrThrow("data_fabricacao")),
        expiryDate = getString(getColumnIndexOrThrow("data_validade")),
        initialQty = getDouble(getColumnIndexOrThrow("quantidade_inicial")),
        availableQty = getDouble(getColumnIndexOrThrow("quantidade_disponivel")),
        reservedQty = getDouble(getColumnIndexOrThrow("quantidade_reservada")),
        discount = getDouble(getColumnIndexOrThrow("desconto_percentual")),
        status = getString(getColumnIndexOrThrow("status")),
        productName = getString(getColumnIndexOrThrow("produto_nome")),
        category = getString(getColumnIndexOrThrow("categoria")),
        unit = getString(getColumnIndexOrThrow("unidade_medida")),
        cost = getDouble(getColumnIndexOrThrow("custo")),
        price = getDouble(getColumnIndexOrThrow("preco_venda")),
        supplier = getString(getColumnIndexOrThrow("fornecedor"))
    )

    fun alerts(): List<AlertItem> {
        expireLotStatuses()
        val s = settings()
        return AlertEngine.classify(lots(), s.attentionDays, s.urgentDays)
    }

    fun refreshAlerts(): List<AlertItem> {
        expireLotStatuses()
        return alerts()
    }

    private fun expireLotStatuses() {
        writableDatabase.execSQL(
            "UPDATE lotes SET status='vencido' WHERE status='ativo' AND data_validade < ? AND quantidade_disponivel > 0",
            arrayOf(todayIso())
        )
    }

    fun addProductAndLot(
        name: String, category: String, unit: String, cost: Double, price: Double, supplier: String,
        lotCode: String, manufactureDate: String, expiryDate: String, initialQty: Double, discount: Double
    ): Lot {
        validateProduct(name, category, unit, cost, price, supplier)
        validateLot(lotCode, manufactureDate, expiryDate, initialQty, discount)
        val productId = UUID.randomUUID().toString()
        val lotId = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("produtos", null, ContentValues().apply {
                put("id", productId); put("nome", name.trim()); put("categoria", category.trim())
                put("unidade_medida", unit.trim()); put("custo", cost); put("preco_venda", price)
                put("fornecedor", supplier.trim()); put("criado_em", System.currentTimeMillis())
            })
            db.insertOrThrow("lotes", null, lotValues(productId, lotCode, manufactureDate, expiryDate, initialQty, discount).apply { put("id", lotId) })
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return lot(lotId) ?: error("Lote não encontrado após cadastro.")
    }

    fun addLotToProduct(
        productId: String, lotCode: String, manufactureDate: String, expiryDate: String,
        initialQty: Double, discount: Double
    ): Lot {
        validateLot(lotCode, manufactureDate, expiryDate, initialQty, discount)
        val lotId = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("lotes", null, lotValues(productId, lotCode, manufactureDate, expiryDate, initialQty, discount).apply { put("id", lotId) })
        return lot(lotId) ?: error("Lote não encontrado após cadastro.")
    }

    private fun lotValues(productId: String, code: String, manufacture: String, expiry: String, qty: Double, discount: Double) = ContentValues().apply {
        put("produto_id", productId); put("codigo_lote", code.trim()); put("data_fabricacao", manufacture)
        put("data_validade", expiry); put("quantidade_inicial", qty); put("quantidade_disponivel", qty)
        put("quantidade_reservada", 0.0); put("desconto_percentual", discount); put("status", "ativo")
        put("criado_em", System.currentTimeMillis())
    }

    private fun validateProduct(name: String, category: String, unit: String, cost: Double, price: Double, supplier: String) {
        require(name.trim().length >= 2) { "Informe o nome do produto." }
        require(category.trim().length >= 2) { "Informe a categoria." }
        require(unit.trim().isNotEmpty()) { "Informe a unidade de medida." }
        require(cost >= 0) { "Custo não pode ser negativo." }
        require(price > 0) { "Preço de venda deve ser maior que zero." }
        require(supplier.trim().length >= 2) { "Informe o fornecedor ou Produção Própria." }
    }

    private fun validateLot(code: String, manufacture: String, expiry: String, qty: Double, discount: Double) {
        require(code.trim().length >= 2) { "Código do lote é obrigatório." }
        require(runCatching { LocalDate.parse(manufacture, ISO) }.isSuccess) { "Data de fabricação inválida." }
        require(runCatching { LocalDate.parse(expiry, ISO) }.isSuccess) { "Data de validade inválida." }
        require(LocalDate.parse(expiry).isAfter(LocalDate.parse(manufacture))) { "A validade deve ser posterior à fabricação." }
        require(qty > 0) { "Quantidade inicial deve ser maior que zero." }
        require(discount in 0.0..100.0) { "Desconto deve estar entre 0 e 100%." }
    }

    fun movements(limit: Int = 100): List<Movement> {
        val sql = """
            SELECT m.*, l.codigo_lote, p.nome AS produto_nome, p.unidade_medida, i.nome AS instituicao_nome
            FROM movimentacoes m
            JOIN lotes l ON l.id = m.lote_id
            JOIN produtos p ON p.id = l.produto_id
            LEFT JOIN instituicoes i ON i.id = m.instituicao_id
            ORDER BY m.criado_em DESC LIMIT ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(limit.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.toMovement()) }
        }
    }

    private fun Cursor.toMovement() = Movement(
        id = getString(getColumnIndexOrThrow("id")),
        lotId = getString(getColumnIndexOrThrow("lote_id")),
        type = MovementType.fromDb(getString(getColumnIndexOrThrow("tipo"))),
        quantity = getDouble(getColumnIndexOrThrow("quantidade")),
        valueReceived = if (isNull(getColumnIndexOrThrow("valor_recebido"))) null else getDouble(getColumnIndexOrThrow("valor_recebido")),
        paymentMethod = getString(getColumnIndexOrThrow("forma_pagamento")),
        institutionId = getString(getColumnIndexOrThrow("instituicao_id")),
        institutionName = getString(getColumnIndexOrThrow("instituicao_nome")),
        reason = getString(getColumnIndexOrThrow("motivo")),
        note = getString(getColumnIndexOrThrow("observacao")),
        createdAt = getLong(getColumnIndexOrThrow("criado_em")),
        lotCode = getString(getColumnIndexOrThrow("codigo_lote")),
        productName = getString(getColumnIndexOrThrow("produto_nome")),
        unit = getString(getColumnIndexOrThrow("unidade_medida"))
    )

    /** Simula a baixa sem persistir; usada para mostrar a confirmação. */
    fun simulateMovement(lotId: String, type: MovementType, qty: Double): String {
        val l = lot(lotId) ?: throw IllegalArgumentException("Lote não encontrado.")
        require(qty > 0) { "Quantidade deve ser maior que zero." }
        if (type != MovementType.ADJUSTMENT && qty > l.freeQty) {
            throw IllegalArgumentException("Quantidade solicitada (${formatQty(qty)} ${l.unit}) excede o saldo livre (${formatQty(l.freeQty)} ${l.unit}, ${formatQty(l.reservedQty)} reservadas).")
        }
        return "Após a baixa restarão ${formatQty((l.availableQty - qty).coerceAtLeast(0.0))} ${l.unit} no lote."
    }

    fun registerMovement(
        lotId: String, type: MovementType, qty: Double, valueReceived: Double?,
        paymentMethod: String?, institutionId: String?, reason: String?, note: String?
    ): Movement {
        require(qty > 0) { "Quantidade deve ser maior que zero." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val l = lot(lotId) ?: throw IllegalArgumentException("Lote não encontrado.")
            if (type != MovementType.ADJUSTMENT && qty > l.freeQty) {
                throw IllegalArgumentException("Bloqueio de segurança: a baixa de ${formatQty(qty)} excede o saldo livre de ${formatQty(l.freeQty)} ${l.unit} (${formatQty(l.reservedQty)} reservadas).")
            }
            if (type == MovementType.SALE && valueReceived != null) require(valueReceived >= 0) { "Valor recebido não pode ser negativo." }
            val movementId = UUID.randomUUID().toString()
            db.insertOrThrow("movimentacoes", null, ContentValues().apply {
                put("id", movementId); put("lote_id", lotId); put("tipo", type.db); put("quantidade", qty)
                put("valor_recebido", valueReceived); put("forma_pagamento", paymentMethod)
                put("instituicao_id", institutionId); put("motivo", reason); put("observacao", note)
                put("criado_em", System.currentTimeMillis())
            })
            val newQty = (l.availableQty - qty).coerceAtLeast(0.0)
            db.update("lotes", ContentValues().apply {
                put("quantidade_disponivel", newQty)
                put("status", if (newQty <= 0.0) "esgotado" else l.status)
            }, "id=?", arrayOf(lotId))
            db.setTransactionSuccessful()
            return Movement(
                id = movementId, lotId = lotId, type = type, quantity = qty, valueReceived = valueReceived,
                paymentMethod = paymentMethod, institutionId = institutionId, institutionName = null,
                reason = reason, note = note, createdAt = System.currentTimeMillis(), lotCode = l.code,
                productName = l.productName, unit = l.unit
            )
        } finally { db.endTransaction() }
    }

    fun institutions(): List<DonationInstitution> =
        readableDatabase.query("instituicoes", null, null, null, null, null, "nome").use { c ->
            buildList {
                while (c.moveToNext()) add(DonationInstitution(
                    c.getString(c.getColumnIndexOrThrow("id")),
                    c.getString(c.getColumnIndexOrThrow("nome")),
                    c.getString(c.getColumnIndexOrThrow("contato")),
                    c.getString(c.getColumnIndexOrThrow("telefone"))
                ))
            }
        }

    fun addInstitution(name: String, contact: String?, phone: String?): DonationInstitution {
        require(name.trim().length >= 3) { "Nome da instituição deve ter pelo menos 3 caracteres." }
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("instituicoes", null, ContentValues().apply {
            put("id", id); put("nome", name.trim()); put("contato", contact); put("telefone", phone)
        })
        return DonationInstitution(id, name.trim(), contact, phone)
    }

    fun offers(): List<Offer> {
        val s = settings()
        expireReservations()
        val today = LocalDate.now()
        val limit = today.plusDays(s.showcaseDays.toLong())
        return lots().asSequence()
            .filter { it.status == "ativo" && it.freeQty > 0 }
            .filter { runCatching { !LocalDate.parse(it.expiryDate).isBefore(today) && !LocalDate.parse(it.expiryDate).isAfter(limit) }.getOrDefault(false) }
            .map { Offer(it, AlertEngine.daysRemaining(it.expiryDate), AlertEngine.discountedPrice(it.price, it.discount), it.freeQty) }
            .sortedBy { it.lot.expiryDate }
            .toList()
    }

    fun createReservation(lotId: String, qty: Double, name: String, phone: String): Reservation {
        require(qty > 0 && qty <= 50) { "Quantidade deve ser entre 1 e 50." }
        require(name.trim().length >= 3) { "Informe o nome completo do cliente." }
        require(phone.filter { it.isDigit() }.length >= 10) { "Informe telefone com DDD." }
        expireReservations()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val l = lot(lotId) ?: throw IllegalArgumentException("Lote indisponível.")
            if (l.freeQty < qty) throw IllegalArgumentException("Restam apenas ${formatQty(l.freeQty)} ${l.unit} disponíveis para reserva.")
            val now = System.currentTimeMillis()
            val expires = now + 2L * 60 * 60 * 1000
            val id = UUID.randomUUID().toString()
            val code = generateReservationCode()
            db.insertOrThrow("reservas", null, ContentValues().apply {
                put("id", id); put("codigo", code); put("lote_id", lotId); put("quantidade", qty)
                put("nome_cliente", name.trim()); put("telefone_cliente", phone.trim())
                put("criado_em", now); put("expira_em", expires); put("status", "ativa")
            })
            db.update("lotes", ContentValues().apply { put("quantidade_reservada", l.reservedQty + qty) }, "id=?", arrayOf(lotId))
            db.setTransactionSuccessful()
            return Reservation(
                id, code, lotId, qty, name.trim(), phone.trim(), now, expires, "ativa",
                l.productName, l.unit, AlertEngine.discountedPrice(l.price, l.discount)
            )
        } finally { db.endTransaction() }
    }

    private fun generateReservationCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        repeat(10) {
            val code = "CMC-" + (1..6).map { chars.random() }.joinToString("")
            val exists = readableDatabase.rawQuery("SELECT 1 FROM reservas WHERE codigo=?", arrayOf(code)).use { it.moveToFirst() }
            if (!exists) return code
        }
        return "CMC-" + System.currentTimeMillis().toString().takeLast(6)
    }

    fun expireReservations(): Int {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "UPDATE lotes SET quantidade_reservada = MAX(0, quantidade_reservada - COALESCE((" +
                    "SELECT SUM(r.quantidade) FROM reservas r WHERE r.lote_id = lotes.id " +
                    "AND r.status='ativa' AND r.expira_em <= CAST(? AS INTEGER)), 0)) " +
                    "WHERE id IN (SELECT lote_id FROM reservas WHERE status='ativa' AND expira_em <= CAST(? AS INTEGER))",
                arrayOf(now.toString(), now.toString())
            )
            val count = db.update(
                "reservas",
                ContentValues().apply { put("status", "expirada") },
                "status='ativa' AND expira_em <= CAST(? AS INTEGER)",
                arrayOf(now.toString())
            )
            db.setTransactionSuccessful()
            return count
        } finally {
            db.endTransaction()
        }
    }

    fun reservations(): List<Reservation> {
        val sql = """
            SELECT r.*, p.nome AS produto_nome, p.unidade_medida, l.desconto_percentual, p.preco_venda
            FROM reservas r JOIN lotes l ON l.id=r.lote_id JOIN produtos p ON p.id=l.produto_id
            ORDER BY r.criado_em DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) add(Reservation(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    code = c.getString(c.getColumnIndexOrThrow("codigo")),
                    lotId = c.getString(c.getColumnIndexOrThrow("lote_id")),
                    quantity = c.getDouble(c.getColumnIndexOrThrow("quantidade")),
                    customerName = c.getString(c.getColumnIndexOrThrow("nome_cliente")),
                    customerPhone = c.getString(c.getColumnIndexOrThrow("telefone_cliente")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("criado_em")),
                    expiresAt = c.getLong(c.getColumnIndexOrThrow("expira_em")),
                    status = c.getString(c.getColumnIndexOrThrow("status")),
                    productName = c.getString(c.getColumnIndexOrThrow("produto_nome")),
                    unit = c.getString(c.getColumnIndexOrThrow("unidade_medida")),
                    unitPrice = AlertEngine.discountedPrice(
                        c.getDouble(c.getColumnIndexOrThrow("preco_venda")),
                        c.getDouble(c.getColumnIndexOrThrow("desconto_percentual"))
                    )
                ))
            }
        }
    }

    fun queryReservation(code: String): Reservation? = reservations().firstOrNull { it.code.equals(code.trim(), true) }

    fun report(start: Long, end: Long): Report {
        val movements = movementsBetween(start, end)
        var soldQ = 0.0; var soldV = 0.0; var donatedQ = 0.0; var donatedV = 0.0; var discardedQ = 0.0; var discardedV = 0.0
        val categories = mutableMapOf<String, CategoryReport>()
        val payments = mutableMapOf<String, PaymentReport>()
        movements.forEach { m ->
            val lot = lot(m.lotId)
            val category = lot?.category ?: "Outros"
            val price = lot?.price ?: 0.0
            val cost = lot?.cost ?: 0.0
            val discount = lot?.discount ?: 0.0
            when (m.type) {
                MovementType.SALE -> {
                    soldQ += m.quantity
                    val value = m.valueReceived ?: (m.quantity * price * (1 - discount / 100))
                    soldV += value
                    val current = categories[category] ?: CategoryReport(category, 0.0, 0.0, 0.0, 0.0)
                    categories[category] = current.copy(soldQty = current.soldQty + m.quantity, soldValue = current.soldValue + value)
                    val pm = m.paymentMethod ?: "outro"
                    val p = payments[pm] ?: PaymentReport(pm, 0, 0.0)
                    payments[pm] = p.copy(count = p.count + 1, total = p.total + value)
                }
                MovementType.DONATION -> {
                    donatedQ += m.quantity
                    donatedV += m.quantity * price
                    val current = categories[category] ?: CategoryReport(category, 0.0, 0.0, 0.0, 0.0)
                    categories[category] = current.copy(donatedQty = current.donatedQty + m.quantity)
                }
                MovementType.DISPOSAL -> {
                    discardedQ += m.quantity
                    discardedV += m.quantity * cost
                    val current = categories[category] ?: CategoryReport(category, 0.0, 0.0, 0.0, 0.0)
                    categories[category] = current.copy(discardedQty = current.discardedQty + m.quantity)
                }
                MovementType.ADJUSTMENT -> Unit
            }
        }
        return Report(start, end, soldQ, soldV, donatedQ, donatedV, discardedQ, discardedV,
            categories.values.sortedByDescending { it.soldValue },
            payments.values.sortedByDescending { it.total })
    }

    private fun movementsBetween(start: Long, end: Long): List<Movement> {
        val sql = """
            SELECT m.*, l.codigo_lote, p.nome AS produto_nome, p.unidade_medida, i.nome AS instituicao_nome
            FROM movimentacoes m JOIN lotes l ON l.id=m.lote_id JOIN produtos p ON p.id=l.produto_id
            LEFT JOIN instituicoes i ON i.id=m.instituicao_id
            WHERE m.criado_em BETWEEN CAST(? AS INTEGER) AND CAST(? AS INTEGER) ORDER BY m.criado_em
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(start.toString(), end.toString())).use { c ->
            buildList { while (c.moveToNext()) add(c.toMovement()) }
        }
    }

    fun csv(start: Long, end: Long): String {
        val rows = movementsBetween(start, end)
        val header = "Data/Hora;Tipo;Produto;Lote;Quantidade;Unidade;Valor;Pagamento;Instituição;Motivo;Observação"
        return buildString {
            appendLine(header)
            rows.forEach { m ->
                appendLine(listOf(
                    formatDateTime(m.createdAt), m.type.label, m.productName, m.lotCode,
                    formatQty(m.quantity), m.unit, String.format(BR_LOCALE, "%.2f", m.valueReceived ?: 0.0),
                    paymentLabel(m.paymentMethod), m.institutionName.orEmpty(), m.reason.orEmpty(), m.note.orEmpty()
                ).joinToString(";") { "\"${it.replace("\"", "\"\"")}\"" })
            }
        }
    }

    fun seedDemo() {
        val db = writableDatabase
        if (db.rawQuery("SELECT COUNT(*) FROM produtos", null).use { it.moveToFirst() && it.getInt(0) > 0 }) return
        seedDemo(db)
    }

    fun wipeAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DROP TRIGGER IF EXISTS trg_mov_no_update")
            db.execSQL("DROP TRIGGER IF EXISTS trg_mov_no_delete")
            db.delete("movimentacoes", null, null)
            db.delete("reservas", null, null)
            db.delete("lotes", null, null)
            db.delete("produtos", null, null)
            db.delete("instituicoes", null, null)
            db.delete("configuracoes", null, null)
            createMovementTriggers(db)
            insertDefaultSettings(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun seedDemo(db: SQLiteDatabase) {
        val now = LocalDate.now()
        fun d(days: Long) = now.plusDays(days).format(ISO)
        val nowMs = System.currentTimeMillis()
        val products = listOf(
            arrayOf("p1", "Iogurte Natural 170g", "Laticínios", "un", 2.10, 4.80, "Laticínios da Serra"),
            arrayOf("p2", "Pão de Forma Integral 500g", "Panificação", "pct", 4.50, 9.90, "Panificadora Bella"),
            arrayOf("p3", "Queijo Minas Frescal 300g", "Frios & Laticínios", "un", 8.90, 18.50, "Fazenda Bonfim"),
            arrayOf("p4", "Suco de Laranja 1L", "Bebidas", "un", 5.20, 11.90, "Pomar Verde"),
            arrayOf("p5", "Vitamina C 10 comp", "Farmácia", "cx", 7.00, 16.90, "Farmacêutica União"),
            arrayOf("p6", "Bolo de Cenoura", "Confeitaria", "un", 6.00, 15.00, "Produção Própria")
        )
        products.forEach { p ->
            db.insertOrThrow("produtos", null, ContentValues().apply {
                put("id", p[0] as String); put("nome", p[1] as String); put("categoria", p[2] as String)
                put("unidade_medida", p[3] as String); put("custo", p[4] as Double); put("preco_venda", p[5] as Double)
                put("fornecedor", p[6] as String); put("criado_em", nowMs)
            })
        }
        val lots = listOf(
            arrayOf("l1", "p1", "LT-IOG-089", d(-20), d(1), 30.0, 18.0, 2.0, 40.0, "ativo"),
            arrayOf("l2", "p2", "LT-PAO-202", d(-5), d(2), 25.0, 12.0, 0.0, 35.0, "ativo"),
            arrayOf("l3", "p3", "LT-QUE-411", d(-15), d(4), 20.0, 10.0, 0.0, 30.0, "ativo"),
            arrayOf("l4", "p4", "LT-SUC-305", d(-25), d(6), 40.0, 24.0, 0.0, 25.0, "ativo"),
            arrayOf("l5", "p5", "LT-VIT-789", d(-100), d(45), 50.0, 50.0, 0.0, 0.0, "ativo"),
            arrayOf("l6", "p6", "LT-BOL-101", d(-10), d(-2), 15.0, 5.0, 0.0, 50.0, "vencido")
        )
        lots.forEach { l ->
            db.insertOrThrow("lotes", null, ContentValues().apply {
                put("id", l[0] as String); put("produto_id", l[1] as String); put("codigo_lote", l[2] as String)
                put("data_fabricacao", l[3] as String); put("data_validade", l[4] as String)
                put("quantidade_inicial", l[5] as Double); put("quantidade_disponivel", l[6] as Double)
                put("quantidade_reservada", l[7] as Double); put("desconto_percentual", l[8] as Double)
                put("status", l[9] as String); put("criado_em", nowMs)
            })
        }
        val inst1 = UUID.randomUUID().toString()
        db.insertOrThrow("instituicoes", null, ContentValues().apply {
            put("id", inst1); put("nome", "Mesa Brasil Sesc"); put("contato", "Mariana"); put("telefone", "(11) 98765-4321")
        })
        val inst2 = UUID.randomUUID().toString()
        db.insertOrThrow("instituicoes", null, ContentValues().apply {
            put("id", inst2); put("nome", "Lar São Francisco"); put("contato", "Irmã Clara"); put("telefone", "(11) 97654-3210")
        })
        db.insertOrThrow("movimentacoes", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("lote_id", "l1"); put("tipo", "venda")
            put("quantidade", 10.0); put("valor_recebido", 28.80); put("forma_pagamento", "pix")
            put("criado_em", nowMs - 86_400_000L)
        })
        db.insertOrThrow("movimentacoes", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("lote_id", "l2"); put("tipo", "doacao")
            put("quantidade", 13.0); put("instituicao_id", inst1)
            put("criado_em", nowMs - 2 * 86_400_000L)
        })
        db.insertOrThrow("movimentacoes", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("lote_id", "l6"); put("tipo", "descarte")
            put("quantidade", 10.0); put("motivo", "forte")
            put("criado_em", nowMs - 86_400_000L)
        })
    }

    companion object { private const val DB_NAME = "controle_maximo.db" }
}

package com.karyaku.kasir

object QrisHelper {
    /**
     * Mengubah QRIS Statis menjadi QRIS Dinamis dengan menyisipkan nominal.
     * @param staticQris String QRIS asli dari toko
     * @param amount Nominal tagihan (Long)
     * @return String QRIS dinamis yang sudah di-update CRC-nya
     */
    fun generateDynamicQris(staticQris: String, amount: Long): String {
        if (staticQris.isBlank()) return ""
        
        // 1. Ubah Point of Initiation Method menjadi 12 (Dynamic)
        var dynamicQris = staticQris.replace("010211", "010212")
        
        // 2. Persiapkan Tag 54 (Transaction Amount)
        val amountStr = amount.toString()
        val amountLen = String.format("%02d", amountStr.length)
        val tag54 = "54$amountLen$amountStr"
        
        // 3. Cari posisi untuk menyisipkan Tag 54 (harus setelah Tag 53 jika ada, atau sebelum Tag 58)
        // Cara paling aman adalah mencari Tag 58 (Country Code) atau 59 (Merchant Name)
        val insertIndex = findInsertIndex(dynamicQris)
        
        if (insertIndex != -1) {
            // Hapus CRC lama (Tag 63 + 4 digit)
            val qrisWithoutCrc = dynamicQris.substring(0, findCrcIndex(dynamicQris))
            
            // Sisipkan Tag 54
            val qrisWithAmount = qrisWithoutCrc.substring(0, insertIndex) + tag54 + qrisWithoutCrc.substring(insertIndex)
            
            // Hitung CRC baru
            val newCrc = calculateCrc16(qrisWithAmount + "6304")
            return qrisWithAmount + "6304" + newCrc
        }
        
        return staticQris // Fallback jika gagal parsing
    }

    private fun findInsertIndex(qris: String): Int {
        // Cari Tag 58 (Country Code) atau 59 (Merchant Name) karena Tag 54 harus disisipkan sebelum ini
        var index = 0
        while (index < qris.length) {
            if (index + 4 <= qris.length) {
                val tag = qris.substring(index, index + 2)
                val len = qris.substring(index + 2, index + 4).toIntOrNull() ?: break
                
                if (tag == "58" || tag == "59" || tag == "60") {
                    return index
                }
                index += 4 + len
            } else break
        }
        return -1
    }

    private fun findCrcIndex(qris: String): Int {
        val crcIndex = qris.lastIndexOf("6304")
        return if (crcIndex != -1) crcIndex else qris.length
    }

    // Algoritma CRC16-CCITT (FALSE) untuk EMVCo
    private fun calculateCrc16(data: String): String {
        var crc = 0xFFFF
        val bytes = data.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xFF shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return String.format("%04X", crc)
    }
}
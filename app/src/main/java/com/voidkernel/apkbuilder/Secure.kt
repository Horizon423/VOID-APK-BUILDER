package com.voidkernel.apkbuilder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores the GitHub token encrypted with an AES-GCM key that lives in the Android Keystore. */
class Vault(ctx: Context) {
    private val p = ctx.getSharedPreferences("void_secure", Context.MODE_PRIVATE)
    private val alias = "void_token_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    fun save(token: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(token.toByteArray(Charsets.UTF_8))
        p.edit().putString("tok", Base64.encodeToString(ct, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP)).apply()
    }

    fun token(): String? {
        return try {
            val ctB64 = p.getString("tok", null)
            val ivB64 = p.getString("iv", null)
            if (ctB64 == null || ivB64 == null) {
                null
            } else {
                val ct = Base64.decode(ctB64, Base64.NO_WRAP)
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                String(c.doFinal(ct), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun hasToken(): Boolean = p.contains("tok")

    fun clear() {
        p.edit().clear().apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        } catch (_: Exception) {
        }
    }
}

/** Non-sensitive settings. */
class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("void_settings", Context.MODE_PRIVATE)
    private fun get(k: String, d: String) = p.getString(k, d) ?: d
    private fun put(k: String, v: String) = p.edit().putString(k, v).apply()

    var login: String get() = get("login", ""); set(v) = put("login", v)
    var defaultRepo: String get() = get("repo", ""); set(v) = put("repo", v)
    var repoBranch: String get() = get("repoBranch", "main"); set(v) = put("repoBranch", v)
    var buildType: String get() = get("buildType", "debug"); set(v) = put("buildType", v)
    var branchOverride: String get() = get("branch", ""); set(v) = put("branch", v)
    var projectDir: String get() = get("projectDir", ""); set(v) = put("projectDir", v)
    var gradleTask: String get() = get("task", ""); set(v) = put("task", v)
    var jdk: String get() = get("jdk", "17"); set(v) = put("jdk", v)
}

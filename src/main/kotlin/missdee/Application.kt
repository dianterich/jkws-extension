package missdee

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.util.JSONObjectUtils
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.sql.DriverManager
import java.util.*

const val fiveMinutes = 5 * 60 * 1000

val json = ContentType.parse("application/json")

suspend fun ApplicationCall.respondJson(text: String) = this.respondText(text, json)

fun RSAKey.encode(): ByteArray {
	val key = this
	val stream = ByteArrayOutputStream()
	ObjectOutputStream(stream).apply {
		writeObject(key)
	}.flush()
	return stream.toByteArray()
}

fun decodeRSAKey(input: ByteArray) = ObjectInputStream(ByteArrayInputStream(input)).readObject() as RSAKey

fun main() {
	val databaseConnection = DriverManager.getConnection("jdbc:sqlite:totally_not_my_privateKeys.db")

	databaseConnection.createStatement().use {
		// Create the table.
		it.executeUpdate("""
			CREATE TABLE IF NOT EXISTS keys(
				kid INTEGER PRIMARY KEY AUTOINCREMENT,
				key BLOB NOT NULL,
				exp INTEGER NOT NULL
			)
		""".trimIndent())
		// Remove any old keys from the tables.
		it.executeUpdate("""
			DELETE FROM keys
		""".trimIndent())
	}

	// Generate three 2048-bit RSA JWKs and store them in the database.
	databaseConnection.prepareStatement("""
		INSERT INTO keys (kid, key, exp) values(?, ?, ?)
	""".trimIndent()).use {
		for (validUntilYear in listOf(2023, 2025, 2027)) {
			it.setInt(1, validUntilYear)
			val expirationTime = GregorianCalendar(validUntilYear, 0, 1).time
			it.setInt(3, expirationTime.time.toInt())
			val key = RSAKeyGenerator(2048)
				.keyID(validUntilYear.toString())
				.expirationTime(expirationTime)
				.generate()
			it.setBytes(2, key.encode())
			it.execute()
		}
	}

	embeddedServer(Netty, port = 8080, module = {
		routing {
			/**
			 * This endpoint returns the JWK set of valid JWKs.
			 */
			get("/.well-known/jwks.json") {
				// Read the keys from the database.
				val keys = databaseConnection.createStatement().executeQuery("""
					SELECT key FROM keys
				""".trimIndent()).use {
					mutableListOf<RSAKey>().apply {
						while (it.next()) {
							add(decodeRSAKey(it.getBytes(1)))
						}
					}
				}
				// Create a set of the valid keys.
				val validKeySet = JWKSet(keys.filter { it.expirationTime.after(Date()) })
				call.respondJson(
					JSONObjectUtils.toJSONString(validKeySet.toJSONObject())
				)
			}

			/**
			 * This endpoint returns a JWT; either signed with a valid key and valid for another five minutes, or signed with
			 * an expired key and valid until five minutes ago.
			 */
			post("/auth") {
				// Check for the expired query parameter.
				val expired = call.request.queryParameters.contains("expired")
				// Read a valid or an expired key from the database, depending on the expired query parameter.
				val key = databaseConnection.createStatement().executeQuery("""
					SELECT key FROM keys WHERE kid = ${if (expired) 2023 else 2025}
				""".trimIndent()).use {
					it.next()
					decodeRSAKey(it.getBytes(1))
				}
				// Create the claim.
				val claimsSet = JWTClaimsSet.Builder()
					.subject("user")
					// Pick the expiration time depending on the expired query parameter.
					.expirationTime(if (expired) Date(Date().time - fiveMinutes) else Date(Date().time + fiveMinutes))
					.build()
				call.respondJson(
					// Create and sign the JWT.
					SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(),  claimsSet)
						.apply { sign(RSASSASigner(key)) }
						.serialize()
				)
			}
		}
	}).start(wait = true)
}
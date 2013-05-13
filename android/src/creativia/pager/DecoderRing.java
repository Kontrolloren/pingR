package creativia.pager;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;

import nl.owlstead.jscl.AssociatedDataSource;
import nl.owlstead.jscl.CCMParameters;
import nl.owlstead.jscl.CipherTextSource;
import nl.owlstead.jscl.PBKDFParameters;
import nl.owlstead.jscl.SaltSource;
import nl.owlstead.jscl.bouncy.PKCS5S2_SHA256_ParametersGenerator;

import org.codehaus.jackson.Base64Variant;
import org.codehaus.jackson.Base64Variants;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.TextNode;
import org.json.JSONObject;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.modes.CCMBlockCipher;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.Strings;
import org.spongycastle.util.encoders.Hex;

import android.util.Log;

public class DecoderRing {

	private static final Base64Variant SJCL_BASE64 = new Base64Variant(Base64Variants.MIME_NO_LINEFEEDS, "SJCL", false, ' ', -1);
	private static final Charset SJCL_CHARSET = Charset.forName("UTF-8");
	private static final int SJCL_IGNORED_TAIL_IV_BYTES = 3;

	private static class SJCLCipherTextStruct implements CCMParameters, PBKDFParameters, CipherTextSource, AssociatedDataSource, SaltSource {
		byte[] iv;
		int v;
		int iter;
		int ks;
		int ts;
		String mode;
		byte[] adata;
		String cipher;
		byte[] salt;
		byte[] ct;

		@Override
		public byte[] getCipherText() {
			return ct;
		}

		@Override
		public byte[] getSalt() {
			return salt;
		}

		@Override
		public int getIterations() {
			return iter;
		}

		@Override
		public byte[] getNonce(int nonceSizeBytes) {
			return Arrays.copyOfRange(iv, 0, nonceSizeBytes);
		}

		@Override
		public int getTagSizeBits() {
			return ts;
		}

		@Override
		public int getKeySize() {
			return ks;
		}

		@Override
		public byte[] getAssociatedData() {
			return adata;
		}

		public int getVersion() {
			return v;
		}
	}

	private static SJCLCipherTextStruct readJsonCipherText(JsonNode rootNode) throws IOException {

		SJCLCipherTextStruct cipherText = new SJCLCipherTextStruct();

		// --- version ---
		cipherText.v = rootNode.findValue("v").asInt();
		if (cipherText.v != 1) {
			throw new IOException("Only version 1 supported");
		}

		// --- password related data ---
		TextNode saltNode = (TextNode) rootNode.path("salt");
		cipherText.salt = saltNode.getBinaryValue(SJCL_BASE64);
		cipherText.iter = rootNode.path("iter").asInt();
		cipherText.ks = rootNode.path("ks").asInt();

		// --- cipher related data ---
		cipherText.cipher = rootNode.path("cipher").asText();
		cipherText.mode = rootNode.path("mode").asText();

		if (!"AES".equalsIgnoreCase(cipherText.cipher)
				|| !"CCM".equalsIgnoreCase(cipherText.mode)) {
			throw new IOException("Only AES/CCM supported");
		}

		cipherText.ts = rootNode.path("ts").asInt();

		// --- actual encrypted data ---

		TextNode ivNode = (TextNode) rootNode.path("iv");
		final byte[] fullIV = ivNode.getBinaryValue(SJCL_BASE64);
		cipherText.iv = Arrays.copyOfRange(fullIV, 0, fullIV.length - SJCL_IGNORED_TAIL_IV_BYTES);

		String adataText = URLDecoder.decode(rootNode.path("adata").asText(), "UTF-8");
		cipherText.adata = adataText.getBytes(SJCL_CHARSET);

		TextNode ctNode = (TextNode) rootNode.path("ct");
		cipherText.ct = ctNode.getBinaryValue(SJCL_BASE64);

		return cipherText;
	}

	public static String decodeMessage(String password, JSONObject obj) {

		final SJCLCipherTextStruct cipherText;


		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode rootNode = mapper.readTree(obj.toString());

			cipherText = readJsonCipherText(rootNode);
		} catch (Exception ex) {
			Log.w("Decoder Ring", "Unable to decode");
			return "";
		} 

		try {
			final KeyParameter keyParam = performPBKDF2BouncyLW(cipherText.getSalt(), password.toCharArray(), cipherText);

			System.out.println(new String(Hex.encode(cipherText.getAssociatedData()), SJCL_CHARSET));

			final org.spongycastle.crypto.params.CCMParameters params = new org.spongycastle.crypto.params.CCMParameters(keyParam, cipherText.ts, cipherText.iv, cipherText.adata);
			final byte[] pt2 = decryptCCM(params, cipherText.ct);
			final String pt2String = new String(pt2, SJCL_CHARSET);
			return pt2String;

		} catch (InvalidCipherTextException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] decryptCCM(final org.spongycastle.crypto.params.CCMParameters params, final byte[] data) throws InvalidCipherTextException {
		final BlockCipher bc = new AESFastEngine();
		final CCMBlockCipher ccm = new CCMBlockCipher(bc);
		ccm.init(false, params);
		final byte[] result = ccm.processPacket(data, 0, data.length);
		return result;
	}

	private static KeyParameter performPBKDF2BouncyLW(byte[] salt, char[] charArray, PBKDFParameters p) {

		// S2 *is* PBKDF2, but the default used only HMAC(SHA-1)
		final PKCS5S2_SHA256_ParametersGenerator gen = new PKCS5S2_SHA256_ParametersGenerator();

		// lets not use String, as we cannot destroy strings, BC to the rescue!
		final byte[] pwBytes = Strings.toUTF8ByteArray(charArray);

		gen.init(pwBytes, salt, p.getIterations());
		final KeyParameter params = (KeyParameter) gen.generateDerivedMacParameters(p.getKeySize());

		// use for/next loop for older Java versions
		java.util.Arrays.fill(pwBytes, 0, pwBytes.length, (byte) 0);

		// returns the bytes within the key, so do not destroy key
		return params;
	}
}

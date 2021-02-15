package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SSLError;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.ssl.ALPNHelper;
import com.oracle.graal.python.builtins.objects.ssl.CertUtils;
import static com.oracle.graal.python.builtins.objects.ssl.CertUtils.getCertificates;
import com.oracle.graal.python.builtins.objects.ssl.SSLCipher;
import com.oracle.graal.python.builtins.objects.ssl.SSLCipherSelector;
import com.oracle.graal.python.builtins.objects.ssl.SSLCipherStringMapping;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorCode;
import com.oracle.graal.python.builtins.objects.ssl.SSLMethod;
import com.oracle.graal.python.builtins.objects.ssl.SSLOptions;
import com.oracle.graal.python.builtins.objects.ssl.SSLProtocol;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import java.io.BufferedReader;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

@CoreFunctions(defineModule = "_ssl")
public class SSLModuleBuiltins extends PythonBuiltins {
    // Taken from CPython
    static final String DEFAULT_CIPHER_STRING = "DEFAULT:!aNULL:!eNULL:!MD5:!3DES:!DES:!RC4:!IDEA:!SEED:!aDSS:!SRP:!PSK";

    public static SSLCipher[] defaultCiphers;
    public static List<String> supportedProtocols;

    public static final int SSL_CERT_NONE = 0;
    public static final int SSL_CERT_OPTIONAL = 1;
    public static final int SSL_CERT_REQUIRED = 2;

    public static final int PROTO_MINIMUM_SUPPORTED = -2;
    public static final int PROTO_MAXIMUM_SUPPORTED = -1;

    private static final int X509_V_FLAG_CRL_CHECK = 0x4;
    private static final int X509_V_FLAG_CRL_CHECK_ALL = 0x8;
    private static final int X509_V_FLAG_X509_STRICT = 0x20;
    public static final int X509_V_FLAG_TRUSTED_FIRST = 0x8000;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        try {
            SSLParameters supportedSSLParameters = SSLContext.getDefault().getSupportedSSLParameters();
            List<String> protocols = Arrays.stream(SSLProtocol.values()).map(SSLProtocol::getName).collect(Collectors.toList());
            protocols.retainAll(Arrays.asList(supportedSSLParameters.getProtocols()));
            // TODO JDK supports it, but we would need to make sure that all the related facilities
            // work
            protocols.remove(SSLProtocol.TLSv1_3.getName());
            supportedProtocols = Collections.unmodifiableList(protocols);

            defaultCiphers = SSLCipherSelector.selectCiphers(null, DEFAULT_CIPHER_STRING);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    // TODO use initialize instead?
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule module = core.lookupBuiltinModule("_ssl");
        PythonObjectFactory factory = PythonObjectFactory.getUncached();
        // TODO decide which values to pick
        module.setAttribute("OPENSSL_VERSION_NUMBER", 269488287);
        PTuple versionInfo = factory.createTuple(new int[]{1, 1, 1, 9, 15});
        module.setAttribute("OPENSSL_VERSION_INFO", versionInfo);
        module.setAttribute("OPENSSL_VERSION", "Java");
        module.setAttribute("_DEFAULT_CIPHERS", DEFAULT_CIPHER_STRING);
        module.setAttribute("_OPENSSL_API_VERSION", versionInfo);

        module.setAttribute("CERT_NONE", SSL_CERT_NONE);
        module.setAttribute("CERT_OPTIONAL", SSL_CERT_OPTIONAL);
        module.setAttribute("CERT_REQUIRED", SSL_CERT_REQUIRED);

        module.setAttribute("HAS_SNI", true);
        // TODO enable
        module.setAttribute("HAS_ECDH", false);
        // TODO enable
        module.setAttribute("HAS_NPN", false);
        module.setAttribute("HAS_ALPN", ALPNHelper.hasAlpn());
        boolean hasSSLv2 = supportedProtocols.contains(SSLProtocol.SSLv2.getName());
        module.setAttribute("HAS_SSLv2", hasSSLv2);
        boolean hasSSLv3 = supportedProtocols.contains(SSLProtocol.SSLv3.getName());
        module.setAttribute("HAS_SSLv3", hasSSLv3);
        module.setAttribute("HAS_TLSv1", supportedProtocols.contains(SSLProtocol.TLSv1.getName()));
        module.setAttribute("HAS_TLSv1_1", supportedProtocols.contains(SSLProtocol.TLSv1_1.getName()));
        module.setAttribute("HAS_TLSv1_2", supportedProtocols.contains(SSLProtocol.TLSv1_2.getName()));
        module.setAttribute("HAS_TLSv1_3", supportedProtocols.contains(SSLProtocol.TLSv1_3.getName()));

        module.setAttribute("PROTO_MINIMUM_SUPPORTED", PROTO_MINIMUM_SUPPORTED);
        module.setAttribute("PROTO_MAXIMUM_SUPPORTED", PROTO_MAXIMUM_SUPPORTED);
        module.setAttribute("PROTO_SSLv3", SSLProtocol.SSLv3.getId());
        module.setAttribute("PROTO_TLSv1", SSLProtocol.TLSv1.getId());
        module.setAttribute("PROTO_TLSv1_1", SSLProtocol.TLSv1_1.getId());
        module.setAttribute("PROTO_TLSv1_2", SSLProtocol.TLSv1_2.getId());
        module.setAttribute("PROTO_TLSv1_3", SSLProtocol.TLSv1_3.getId());

        if (hasSSLv2) {
            module.setAttribute("PROTOCOL_SSLv2", SSLMethod.SSL2.getPythonId());
        }
        if (hasSSLv3) {
            module.setAttribute("PROTOCOL_SSLv3", SSLMethod.SSL3.getPythonId());
        }
        module.setAttribute("PROTOCOL_SSLv23", SSLMethod.TLS.getPythonId());
        module.setAttribute("PROTOCOL_TLS", SSLMethod.TLS.getPythonId());
        module.setAttribute("PROTOCOL_TLS_CLIENT", SSLMethod.TLS_CLIENT.getPythonId());
        module.setAttribute("PROTOCOL_TLS_SERVER", SSLMethod.TLS_SERVER.getPythonId());
        module.setAttribute("PROTOCOL_TLSv1", SSLMethod.TLS1.getPythonId());
        module.setAttribute("PROTOCOL_TLSv1_1", SSLMethod.TLS1_1.getPythonId());
        module.setAttribute("PROTOCOL_TLSv1_2", SSLMethod.TLS1_2.getPythonId());

        module.setAttribute("SSL_ERROR_SSL", SSLErrorCode.ERROR_SSL.getErrno());
        module.setAttribute("SSL_ERROR_WANT_READ", SSLErrorCode.ERROR_WANT_READ.getErrno());
        module.setAttribute("SSL_ERROR_WANT_WRITE", SSLErrorCode.ERROR_WANT_WRITE.getErrno());
        module.setAttribute("SSL_ERROR_WANT_X509_LOOKUP", SSLErrorCode.ERROR_WANT_X509_LOOKUP.getErrno());
        module.setAttribute("SSL_ERROR_SYSCALL", SSLErrorCode.ERROR_SYSCALL.getErrno());
        module.setAttribute("SSL_ERROR_ZERO_RETURN", SSLErrorCode.ERROR_ZERO_RETURN.getErrno());
        module.setAttribute("SSL_ERROR_WANT_CONNECT", SSLErrorCode.ERROR_WANT_CONNECT.getErrno());
        module.setAttribute("SSL_ERROR_EOF", SSLErrorCode.ERROR_EOF.getErrno());
        module.setAttribute("SSL_ERROR_INVALID_ERROR_CODE", 10);

        module.setAttribute("OP_ALL", SSLOptions.DEFAULT_OPTIONS);
        module.setAttribute("OP_NO_SSLv2", SSLOptions.SSL_OP_NO_SSLv2);
        module.setAttribute("OP_NO_SSLv3", SSLOptions.SSL_OP_NO_SSLv3);
        module.setAttribute("OP_NO_TLSv1", SSLOptions.SSL_OP_NO_TLSv1);
        module.setAttribute("OP_NO_TLSv1_1", SSLOptions.SSL_OP_NO_TLSv1_1);
        module.setAttribute("OP_NO_TLSv1_2", SSLOptions.SSL_OP_NO_TLSv1_2);
        module.setAttribute("OP_NO_TLSv1_3", SSLOptions.SSL_OP_NO_TLSv1_3);

        module.setAttribute("VERIFY_DEFAULT", 0);
        module.setAttribute("VERIFY_CRL_CHECK_LEAF", X509_V_FLAG_CRL_CHECK);
        module.setAttribute("VERIFY_CRL_CHECK_CHAIN", X509_V_FLAG_CRL_CHECK | X509_V_FLAG_CRL_CHECK_ALL);
        module.setAttribute("VERIFY_X509_STRICT", X509_V_FLAG_X509_STRICT);
        module.setAttribute("VERIFY_X509_TRUSTED_FIRST", X509_V_FLAG_TRUSTED_FIRST);
    }

    @Builtin(name = "txt2obj", minNumOfPositionalArgs = 1, parameterNames = {"txt", "name"})
    @ArgumentClinic(name = "txt", conversion = ArgumentClinic.ClinicConversion.String)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class Txt2ObjNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object txt2obj(String txt, boolean name) {
            // TODO implement properly
            if ("1.3.6.1.5.5.7.3.1".equals(txt)) {
                return factory().createTuple(new Object[]{129, "serverAuth", "TLS Web Server Authentication", txt});
            } else if ("1.3.6.1.5.5.7.3.2".equals(txt)) {
                return factory().createTuple(new Object[]{130, "clientAuth", "TLS Web Client Authentication", txt});
            }
            throw raise(NotImplementedError);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SSLModuleBuiltinsClinicProviders.Txt2ObjNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "nid2obj", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"nid"})
    @GenerateNodeFactory
    abstract static class Nid2ObjNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object nid2obj(Object nid) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_status")
    @GenerateNodeFactory
    abstract static class RandStatusNode extends PythonBuiltinNode {
        @Specialization
        Object randStatus() {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_add", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"string", "entropy"})
    @GenerateNodeFactory
    abstract static class RandAddNode extends PythonBinaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randAdd(Object string, Object entropy) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_bytes", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"n"})
    @GenerateNodeFactory
    abstract static class RandBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randBytes(Object n) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "RAND_pseudo_bytes", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"n"})
    @GenerateNodeFactory
    abstract static class RandPseudoBytesNode extends PythonUnaryBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object randPseudoBytes(Object n) {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "get_default_verify_paths")
    @GenerateNodeFactory
    abstract static class GetDefaultVerifyPathsNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object get() {
            throw raise(NotImplementedError);
        }
    }

    @Builtin(name = "_test_decode_cert", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"path"})
    @GenerateNodeFactory
    abstract static class DecodeCertNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "2")
        Object decode(VirtualFrame frame, Object path,
                        @CachedLibrary("path") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") HashingStorageLibrary hlib) {

            TruffleFile file = toTruffleFile(frame, lib, path);
            List<X509Certificate> l = new ArrayList<>();
            try (BufferedReader r = file.newBufferedReader()) {
                CertUtils.LoadCertError result = getCertificates(r, l);
                if (result != CertUtils.LoadCertError.NO_ERROR) {
                    throw raise(SSLError, "Error decoding PEM-encoded file: " + result);
                }
                if (l.isEmpty()) {
                    throw raise(SSLError, "Error decoding PEM-encoded file");
                }
                return CertUtils.decodeCertificate(l.get(0), hlib, factory());
            } catch (IOException ex) {
                throw raise(SSLError, "Can't open file: " + ex.toString());
            } catch (CertificateException ex) {
                throw raise(SSLError, "Error decoding PEM-encoded file: " + ex.toString());
            }
        }

        private TruffleFile toTruffleFile(VirtualFrame frame, PythonObjectLibrary lib, Object fileObject) throws PException {
            TruffleFile file;
            try {
                file = getContext().getEnv().getPublicTruffleFile(lib.asPath(fileObject));
                if (!file.exists()) {
                    throw raiseOSError(frame, OSErrorEnum.ENOENT);
                }
                return file;
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
        }
    }
}

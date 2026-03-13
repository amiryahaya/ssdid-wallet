import SwiftUI
import AVFoundation

struct ScanQrScreen: View {
    @Environment(AppRouter.self) private var router

    @State private var qrContent = ""
    @State private var hasCameraPermission = false
    @State private var hasScanned = false

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Button { router.pop() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundStyle(Color.textPrimary)
                        .font(.system(size: 20))
                }
                .padding(.leading, 8)

                Text("Scan QR Code")
                    .font(.ssdidHeadline)
                    .foregroundStyle(Color.textPrimary)

                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.trailing, 20)

            Spacer().frame(height: 16)

            // Camera preview
            ZStack {
                if hasCameraPermission {
                    CameraPreviewView { code in
                        guard !hasScanned else { return }
                        hasScanned = true
                        handleQrContent(code)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                } else {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color.bgSecondary)

                    VStack(spacing: 16) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 48))
                            .foregroundStyle(Color.textTertiary)

                        Text("Camera access required")
                            .font(.ssdidBody)
                            .foregroundStyle(Color.textSecondary)

                        Button("Open Settings") {
                            if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                                UIApplication.shared.open(settingsUrl)
                            }
                        }
                        .buttonStyle(.ssdidSecondary)
                    }
                }

                // Scan frame overlay
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.ssdidAccent.opacity(0.6), lineWidth: 3)
                    .frame(width: 240, height: 240)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)

            Spacer().frame(height: 16)

            // Manual QR input for testing
            VStack(alignment: .leading, spacing: 8) {
                Text("MANUAL INPUT")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                TextField("", text: $qrContent, prompt: Text("Paste QR content").foregroundStyle(Color.textTertiary))
                    .textFieldStyle(.plain)
                    .font(.ssdidMono)
                    .foregroundStyle(Color.textPrimary)
                    .padding(14)
                    .background(Color.bgCard)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.ssdidBorder, lineWidth: 1)
                    )

                Button {
                    handleQrContent(qrContent)
                } label: {
                    Text("Process QR")
                }
                .buttonStyle(.ssdidSecondary)
                .disabled(qrContent.isEmpty)
            }
            .padding(.horizontal, 20)

            Spacer().frame(height: 16)

            // Supported formats info
            VStack(alignment: .leading, spacing: 8) {
                Text("SUPPORTED FORMATS")
                    .font(.ssdidCaption)
                    .foregroundStyle(Color.textTertiary)

                qrFormatRow("SSDID Registration", format: "ssdid://register?server_url=...&server_did=...")
                qrFormatRow("SSDID Login", format: "ssdid://login?server_url=...&callback_url=...")
                qrFormatRow("SSDID Authentication", format: "ssdid://authenticate?server_url=...")
                qrFormatRow("SSDID Transaction", format: "ssdid://sign?server_url=...&session_token=...")
                qrFormatRow("Credential Offer", format: "ssdid://credential-offer?issuer_url=...")
            }
            .ssdidCard()
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
        }
        .background(Color.bgPrimary)
        .onAppear {
            checkCameraPermission()
        }
    }

    @ViewBuilder
    private func qrFormatRow(_ label: String, format: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            RoundedRectangle(cornerRadius: 1)
                .fill(Color.ssdidAccent)
                .frame(width: 6, height: 6)
                .padding(.top, 5)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .font(.system(size: 13))
                    .foregroundStyle(Color.textPrimary)
                Text(format)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.textTertiary)
            }
        }
    }

    private func checkCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            hasCameraPermission = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    hasCameraPermission = granted
                }
            }
        default:
            hasCameraPermission = false
        }
    }

    private func handleQrContent(_ content: String) {
        let handler = DeepLinkHandler()
        guard let action = try? handler.parse(urlString: content) else { return }

        switch action {
        case .login(let serverUrl, let serviceName, let challengeId, let callbackUrl, let requestedClaims):
            router.push(.driveLogin(
                serviceUrl: serverUrl,
                serviceName: serviceName ?? "SSDID Drive",
                challengeId: challengeId ?? "",
                callbackUrl: callbackUrl,
                requestedClaims: requestedClaims ?? ""
            ))
        case .register(let serverUrl, let serverDid):
            router.push(.registration(
                serverUrl: serverUrl,
                serverDid: serverDid ?? ""
            ))
        case .authenticate(let serverUrl, let callbackUrl, let sessionId, let requestedClaims, let acceptedAlgorithms):
            if sessionId != nil || requestedClaims != nil {
                router.push(.consent(
                    serverUrl: serverUrl,
                    callbackUrl: callbackUrl,
                    sessionId: sessionId ?? "",
                    requestedClaims: requestedClaims ?? "",
                    acceptedAlgorithms: acceptedAlgorithms
                ))
            } else {
                router.push(.authFlow(
                    serverUrl: serverUrl,
                    callbackUrl: callbackUrl
                ))
            }
        case .sign(let serverUrl, let sessionToken):
            router.push(.txSigning(
                serverUrl: serverUrl,
                sessionToken: sessionToken
            ))
        case .credentialOffer(let issuerUrl, let offerId):
            router.push(.credentialOffer(
                issuerUrl: issuerUrl,
                offerId: offerId
            ))
        case .invite(let serverUrl, let token, let callbackUrl):
            router.push(.inviteAccept(
                serverUrl: serverUrl,
                token: token,
                callbackUrl: callbackUrl
            ))
        }
    }
}

// MARK: - Camera Preview

struct CameraPreviewView: UIViewRepresentable {
    let onCodeScanned: (String) -> Void

    func makeUIView(context: Context) -> CameraPreviewUIView {
        let view = CameraPreviewUIView()
        view.onCodeScanned = onCodeScanned
        return view
    }

    func updateUIView(_ uiView: CameraPreviewUIView, context: Context) {}
}

final class CameraPreviewUIView: UIView {

    var onCodeScanned: ((String) -> Void)?

    private let captureSession = AVCaptureSession()
    private let metadataDelegate = MetadataDelegate()
    private var previewLayer: AVCaptureVideoPreviewLayer?

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupCamera()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupCamera()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        previewLayer?.frame = bounds
    }

    private func setupCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else { return }

        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        }

        let metadataOutput = AVCaptureMetadataOutput()
        if captureSession.canAddOutput(metadataOutput) {
            captureSession.addOutput(metadataOutput)

            metadataDelegate.session = captureSession
            metadataDelegate.onCodeScanned = { [weak self] value in
                DispatchQueue.main.async {
                    self?.onCodeScanned?(value)
                }
            }

            metadataOutput.setMetadataObjectsDelegate(metadataDelegate, queue: DispatchQueue.global(qos: .userInitiated))
            metadataOutput.metadataObjectTypes = [.qr]
        }

        let preview = AVCaptureVideoPreviewLayer(session: captureSession)
        preview.videoGravity = .resizeAspectFill
        layer.addSublayer(preview)
        previewLayer = preview

        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.captureSession.startRunning()
        }
    }
}

/// Separate non-MainActor delegate to avoid sendability issues.
private final class MetadataDelegate: NSObject, AVCaptureMetadataOutputObjectsDelegate {
    nonisolated(unsafe) var session: AVCaptureSession?
    nonisolated(unsafe) var onCodeScanned: ((String) -> Void)?

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              object.type == .qr,
              let value = object.stringValue else { return }

        session?.stopRunning()
        onCodeScanned?(value)
    }
}

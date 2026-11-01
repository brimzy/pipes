import SwiftUI
import ImageIO

struct CoverArt: View {
    var url: URL?
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 16).fill(PipesStyle.surface)
            if let url, url.isFileURL {
                LocalCover(url: url)
            } else {
                AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: {
                    Image(systemName: "music.note").font(.largeTitle).foregroundStyle(.secondary)
                }
            }
        }
        .clipped().clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(.white.opacity(0.12), lineWidth: 1))
        .accessibilityHidden(true)
    }
}

private struct LocalCover: UIViewRepresentable {
    let url: URL
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .scaleAspectFill; view.clipsToBounds = true
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return view
    }
    func makeCoordinator() -> Coordinator { Coordinator() }
    func updateUIView(_ view: UIImageView, context: Context) {
        let key = url.absoluteString + (reduceMotion ? ":still" : ":animated")
        guard context.coordinator.key != key else { return }
        context.coordinator.key = key
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { view.image = nil; return }
        let count = CGImageSourceGetCount(source)
        let stride = max(1, Int(ceil(Double(count) / 40.0)))
        var frames: [UIImage] = []; var duration = 0.0
        for index in 0..<count {
            let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
            let gif = properties?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
            duration += max(0.04, gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double ?? gif?[kCGImagePropertyGIFDelayTime] as? Double ?? 0.1)
            guard index % stride == 0 else { continue }
            let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceThumbnailMaxPixelSize: 384, kCGImageSourceCreateThumbnailWithTransform: true]
            if let cgImage = CGImageSourceCreateThumbnailAtIndex(source, index, options as CFDictionary) { frames.append(UIImage(cgImage: cgImage)) }
            if reduceMotion { break }
        }
        view.image = frames.count > 1 ? UIImage.animatedImage(with: frames, duration: duration) : frames.first
    }
    final class Coordinator { var key: String? }
}

struct MiniPlayer: View {
    @EnvironmentObject var library: MusicLibrary
    @Binding var showingPlayer: Bool
    var body: some View {
        VStack(spacing: 0) {
            if library.current?.videoID != nil {
                YouTubePlayer(player: library.embeddedPlayer).frame(height: 144)
                Text("Online playback needs Pipes open").font(.caption2).foregroundStyle(.secondary).padding(.vertical, 3)
            }
            HStack(spacing: 12) {
                Button { showingPlayer = true } label: {
                    HStack {
                        CoverArt(url: library.current?.artwork).frame(width: 40, height: 40)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(library.current?.title ?? "").font(.subheadline.bold()).lineLimit(1)
                            Text(library.current?.artist ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer(minLength: 0)
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain)
                .gesture(DragGesture(minimumDistance: 30).onEnded { value in
                    guard abs(value.translation.width) > abs(value.translation.height) else { return }
                    if value.translation.width < -45 { library.next() }
                    else if value.translation.width > 45 { library.previous() }
                })
                Button { library.isPlaying ? library.pause() : library.resume() } label: {
                    Image(systemName: library.isPlaying ? "pause.fill" : "play.fill").frame(width: 36, height: 44)
                }.accessibilityLabel(library.isPlaying ? "Pause" : "Play")
                Button { library.next() } label: { Image(systemName: "forward.end.fill").frame(width: 32, height: 44) }
                    .accessibilityLabel("Next song")
                Button { library.stop() } label: { Image(systemName: "xmark").frame(width: 32, height: 44) }
                    .accessibilityLabel("Stop and clear queue")
            }.padding(.horizontal, 12).padding(.vertical, 6)
            ProgressView(value: min(library.position, max(1, library.duration)), total: max(1, library.duration)).tint(.white.opacity(0.7))
        }
        .background(.ultraThinMaterial)
    }
}

struct PlayerView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.addSongs) private var addSongs
    let onMinimize: () -> Void
    let onQueue: () -> Void
    var body: some View {
        ZStack {
            PipesStyle.background.ignoresSafeArea()
            if let artwork = library.current?.artwork {
                AsyncImage(url: artwork) { image in image.resizable().scaledToFill().blur(radius: 70) } placeholder: { Color.clear }
                    .opacity(0.16).ignoresSafeArea().allowsHitTesting(false).accessibilityHidden(true)
            }
            VStack(spacing: 0) {
                HStack {
                    Button(action: onMinimize) { Image(systemName: "chevron.down").frame(width: 44, height: 44) }.accessibilityLabel("Minimize player")
                    Spacer()
                    Text(library.radioSeed == nil ? "NOW PLAYING" : "SONG RADIO").font(.caption.weight(.semibold)).tracking(3)
                    Spacer()
                    Button { library.stop(); onMinimize() } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }.accessibilityLabel("Stop and clear queue")
                }.padding(.horizontal, 12)
                ScrollView {
                    VStack(spacing: 24) {
                        if let track = library.current {
                            if track.videoID != nil {
                                YouTubePlayer(player: library.embeddedPlayer).frame(height: 230).clipShape(RoundedRectangle(cornerRadius: 20))
                                Text("Keep Pipes open for online playback. If playback doesn't start, tap Play in the video.").font(.caption).foregroundStyle(.secondary)
                            } else {
                                CoverArt(url: track.artwork).aspectRatio(1, contentMode: .fit).frame(maxWidth: 360)
                                    .shadow(color: .black.opacity(0.4), radius: 24, y: 12)
                            }
                            VStack(spacing: 8) {
                                Text(track.title).font(.title.bold()).multilineTextAlignment(.center)
                                Text(track.artist).foregroundStyle(.secondary)
                            }
                            WaveformSeek(position: library.position, duration: library.duration, onSeek: library.seek)
                            HStack(spacing: 24) {
                                Button { library.shuffle.toggle() } label: {
                                    Image(systemName: "shuffle").foregroundStyle(library.shuffle ? PipesStyle.accent : .secondary)
                                }.accessibilityLabel(library.shuffle ? "Shuffle on" : "Shuffle off")
                                Button { library.previous() } label: { Image(systemName: "backward.end.fill").font(.title2) }.accessibilityLabel("Previous song")
                                Button { library.isPlaying ? library.pause() : library.resume() } label: {
                                    Image(systemName: library.isPlaying ? "pause.fill" : "play.fill")
                                        .font(.title).foregroundStyle(.black).frame(width: 72, height: 72).background(.white, in: Circle())
                                }.accessibilityLabel(library.isPlaying ? "Pause" : "Play")
                                Button { library.next() } label: { Image(systemName: "forward.end.fill").font(.title2) }.accessibilityLabel("Next song")
                                Button { library.repeatMode = library.repeatMode.next } label: {
                                    Image(systemName: library.repeatMode == .one ? "repeat.1" : "repeat")
                                        .foregroundStyle(library.repeatMode == .off ? .secondary : PipesStyle.accent)
                                }.accessibilityLabel("Repeat \(library.repeatMode.rawValue)")
                            }.frame(maxWidth: .infinity).buttonStyle(.plain)
                            HStack(spacing: 36) {
                                Button { library.toggleFavorite(track) } label: {
                                    Image(systemName: library.isFavorite(track) ? "heart.fill" : "heart").foregroundStyle(library.isFavorite(track) ? PipesStyle.accent : .primary)
                                }.accessibilityLabel(library.isFavorite(track) ? "Unlike song" : "Like song")
                                Button { addSongs([track]) } label: { Image(systemName: "plus") }.accessibilityLabel("Add to playlist or album")
                                Button(action: onQueue) { Image(systemName: "text.line.first.and.arrowtriangle.forward") }.accessibilityLabel("Open queue")
                                if track.videoID != nil {
                                    Button { library.startRadio(track) } label: { Image(systemName: "dot.radiowaves.left.and.right") }.accessibilityLabel("Start radio")
                                }
                            }.font(.title2).buttonStyle(.plain).padding(.vertical, 8)
                            if library.radioLoading { ProgressView("Finding related songs…") }
                            if let id = track.videoID, let url = URL(string: "https://www.youtube.com/watch?v=" + id) {
                                Link("Open in YouTube", destination: url).font(.caption)
                            }
                        }
                    }.padding(24).frame(maxWidth: 620).frame(maxWidth: .infinity)
                }
            }
        }.foregroundStyle(.primary)
    }
}

struct WaveformSeek: View {
    let position: Double
    let duration: Double
    let onSeek: (Double) -> Void
    @State private var preview: Double?
    private var progress: Double { min(1, max(0, (preview ?? position) / max(duration, 1))) }
    var body: some View {
        VStack(spacing: 8) {
            Canvas { context, size in
                let count = 64
                let spacing = size.width / Double(count)
                for index in 0..<count {
                    let x = Double(index) / Double(count - 1)
                    let envelope = pow(sin(x * .pi), 0.7)
                    let shape = 0.22 + 0.78 * abs(sin(Double(index) * 1.7) * cos(Double(index) * 0.31))
                    let height = 5 + (size.height - 10) * envelope * shape
                    let rect = CGRect(x: Double(index) * spacing, y: (size.height - height) / 2, width: max(2, spacing - 2), height: height)
                    context.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(x <= progress ? .white : .white.opacity(0.20)))
                }
            }.frame(height: 76).contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                    guard duration > 0 else { return }
                    preview = min(duration, max(0, Double(value.location.x / max(1, waveformWidth)) * duration))
                }.onEnded { _ in
                    if let preview { onSeek(preview); UISelectionFeedbackGenerator().selectionChanged() }
                    preview = nil
                })
                .background(GeometryReader { geometry in Color.clear.onAppear { waveformWidth = geometry.size.width }.onChange(of: geometry.size.width) { _, width in waveformWidth = width } })
                .accessibilityElement().accessibilityLabel("Playback position")
                .accessibilityValue("\(formatTime(preview ?? position)) of \(formatTime(duration))")
                .accessibilityAdjustableAction { direction in
                    onSeek(position + (direction == .increment ? 10 : -10))
                }
            HStack {
                Text(formatTime(preview ?? position)); Spacer(); Text(formatTime(duration))
            }.font(.caption.monospacedDigit()).foregroundStyle(.secondary)
        }
    }
    @State private var waveformWidth: CGFloat = 1
}

func formatTime(_ value: Double) -> String {
    let seconds = value.isFinite ? Int(max(0, min(value, 359_999))) : 0
    return String(format: "%d:%02d", seconds / 60, seconds % 60)
}

struct QueueView: View {
    @EnvironmentObject var library: MusicLibrary
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                if let current = library.current {
                    Section("Now playing") { Text(current.title).font(.headline); Text(current.artist).foregroundStyle(.secondary) }
                }
                Section("Up next · \(library.queue.count)") {
                    if library.queue.isEmpty { Text("Use a song's menu to Play next or Add to queue.").foregroundStyle(.secondary) }
                    ForEach(library.queue) { track in
                        TrackRow(track: track, songs: library.queue)
                    }
                    .onDelete { library.queue.remove(atOffsets: $0) }
                    .onMove { library.queue.move(fromOffsets: $0, toOffset: $1) }
                }
                if library.radioLoading { ProgressView("Finding related songs…") }
            }.scrollContentBackground(.hidden).background(PipesStyle.background)
                .navigationTitle("Queue").toolbar {
                    ToolbarItem(placement: .topBarLeading) { Button("Done") { dismiss() } }
                    ToolbarItem(placement: .topBarTrailing) { EditButton() }
                }
        }
    }
}

import CarPlay
import Combine
import UIKit

/// CarPlay uses the same audio player as the phone and exposes local audio only.
@MainActor
final class CarPlaySceneDelegate: NSObject, @preconcurrency CPTemplateApplicationSceneDelegate {
    private var library: MusicLibrary { .shared }
    private var interfaceController: CPInterfaceController?
    private var subscriptions = Set<AnyCancellable>()
    private let localTemplate = CPListTemplate(title: "Library", sections: [])
    private let favoritesTemplate = CPListTemplate(title: "Favorites", sections: [])
    private let queueTemplate = CPListTemplate(title: "Queue", sections: [])

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didConnect interfaceController: CPInterfaceController) {
        self.interfaceController = interfaceController
        localTemplate.tabTitle = "Library"
        localTemplate.tabImage = UIImage(systemName: "music.note.list")
        favoritesTemplate.tabTitle = "Favorites"
        favoritesTemplate.tabImage = UIImage(systemName: "heart")
        queueTemplate.tabTitle = "Queue"
        queueTemplate.tabImage = UIImage(systemName: "text.line.first.and.arrowtriangle.forward")
        for template in [localTemplate, favoritesTemplate, queueTemplate] {
            template.emptyViewTitleVariants = ["No local audio"]
            template.emptyViewSubtitleVariants = [
                "Import audio in Pipes on your iPhone. Online embedded videos are unavailable in CarPlay.",
                "Import audio on your iPhone first."
            ]
            template.trailingNavigationBarButtons = [CPBarButton(title: "Now playing") { [weak self] _ in
                Task { @MainActor in self?.showNowPlaying() }
            }]
        }
        refresh()
        interfaceController.setRootTemplate(CPTabBarTemplate(templates: [localTemplate, favoritesTemplate, queueTemplate]), animated: false, completion: nil)
        Publishers.CombineLatest4(library.$tracks, library.$favorites, library.$queue, library.$current)
            .combineLatest(library.$isPlaying)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                Task { @MainActor in self?.refresh() }
            }
            .store(in: &subscriptions)
    }

    func templateApplicationScene(_ templateApplicationScene: CPTemplateApplicationScene,
                                  didDisconnectInterfaceController interfaceController: CPInterfaceController) {
        subscriptions.removeAll()
        self.interfaceController = nil
        // Disconnecting the display does not interrupt the shared audio session.
    }

    private func refresh() {
        localTemplate.updateSections(sections(for: library.carPlayable(library.tracks)))
        favoritesTemplate.updateSections(sections(for: library.carPlayable(library.favorites)))
        queueTemplate.updateSections(sections(for: library.carPlayable(library.queue)))
    }

    private func sections(for tracks: [Track]) -> [CPListSection] {
        let items = tracks.prefix(CPListTemplate.maximumItemCount).enumerated().map { index, track -> CPListItem in
            let item = CPListItem(text: track.title, detailText: track.artist, image: UIImage(systemName: "music.note"))
            item.isPlaying = library.current?.id == track.id && library.isPlaying
            item.handler = { [weak self] _, completion in
                Task { @MainActor in
                    defer { completion() }
                    guard let self else { return }
                    // Validate again in case a file was removed while its row was on screen.
                    guard !self.library.carPlayable([track]).isEmpty else { self.refresh(); return }
                    self.library.queue = Array(tracks.dropFirst(index + 1))
                    self.library.play(track)
                    self.showNowPlaying()
                }
            }
            return item
        }
        return items.isEmpty ? [] : [CPListSection(items: items)]
    }

    private func showNowPlaying() {
        guard let interfaceController, library.current?.filename != nil,
              !(interfaceController.topTemplate is CPNowPlayingTemplate) else { return }
        interfaceController.pushTemplate(CPNowPlayingTemplate.shared, animated: true, completion: nil)
    }
}

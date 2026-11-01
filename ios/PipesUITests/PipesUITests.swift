import XCTest

final class PipesUITests: XCTestCase {
    func testTutorialAndCollectionCreation() {
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["Start tutorial"]
        if !start.waitForExistence(timeout: 5) {
            app.buttons["Settings"].tap()
            app.buttons["Learn Pipes"].tap()
        }
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        app.buttons["Start tutorial"].tap()
        XCTAssertTrue(app.staticTexts["Find your next song"].waitForExistence(timeout: 5))
        app.buttons["Next lesson"].tap()
        XCTAssertTrue(app.staticTexts["Get to know the player"].waitForExistence(timeout: 5))
        app.buttons["Save & close"].tap()
        app.tabBars.buttons["Library"].tap()
        app.buttons["New playlist or album"].tap()
        let name = app.textFields["Collection name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap(); name.typeText("Night drive")
        app.buttons["Save"].tap()
        XCTAssertTrue(app.staticTexts["Night drive"].waitForExistence(timeout: 5))
        app.staticTexts["Night drive"].tap()
        XCTAssertTrue(app.buttons["Choose artwork"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No songs yet"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "iPhone 5.1 collection"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.terminate(); app.launch()
        app.tabBars.buttons["Library"].tap()
        XCTAssertTrue(app.staticTexts["Night drive"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()
        let version = app.descendants(matching: .any)["pipes-version"]
        XCTAssertTrue(version.waitForExistence(timeout: 5))
        XCTAssertEqual(version.label, "Pipes version 5.1.55")
        app.buttons["Learn Pipes"].tap()
        XCTAssertTrue(app.staticTexts["Get to know the player"].waitForExistence(timeout: 5))
    }
}

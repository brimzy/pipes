import XCTest

final class PipesUITests: XCTestCase {
    func testTutorialAndCollectionCreation() {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.buttons["Start tutorial"].waitForExistence(timeout: 15))
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
        XCTAssertTrue(app.staticTexts["5.1.55"].waitForExistence(timeout: 5))
        app.buttons["Learn Pipes"].tap()
        XCTAssertTrue(app.staticTexts["Get to know the player"].waitForExistence(timeout: 5))
    }
}

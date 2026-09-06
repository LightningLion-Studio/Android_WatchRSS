package com.lightningstudio.watchrss.ui.reader

import com.lightningstudio.watchrss.data.reader.ReaderBackgroundFit
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBackgroundPlaneTest {
    @Test fun zoomedMatchingAspectRatioCanPanToEitherEdge() {
        assertEquals(ReaderBackgroundPlane(800, 800, 0, 0), plane(ReaderBackgroundFit.CROP, 2f, 0f))
        assertEquals(ReaderBackgroundPlane(800, 800, -400, -400), plane(ReaderBackgroundFit.CROP, 2f, 1f))
    }
    @Test fun fitKeepsFullFrameAndCropUsesFocusAfterZoom() {
        assertEquals(ReaderBackgroundPlane(400, 200, 0, 100),
            readerBackgroundPlane(400, 400, 800f, 400f, ReaderBackgroundFit.FIT, 1f, .5f, .5f))
        assertEquals(ReaderBackgroundPlane(1600, 800, -1200, -400),
            readerBackgroundPlane(400, 400, 800f, 400f, ReaderBackgroundFit.CROP, 2f, 1f, 1f))
    }
    @Test fun fillUsesViewportAspectAndRotationDoesNotChangeResourceGeometry() {
        assertEquals(ReaderBackgroundPlane(400, 400, 0, 0),
            readerBackgroundPlane(400, 400, 800f, 400f, ReaderBackgroundFit.FILL, 1f, .5f, .5f))
    }
    private fun plane(fit: ReaderBackgroundFit, zoom: Float, focus: Float) =
        readerBackgroundPlane(400, 400, 800f, 800f, fit, zoom, focus, focus)
}

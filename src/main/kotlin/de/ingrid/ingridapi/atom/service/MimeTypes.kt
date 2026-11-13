package de.ingrid.ingridapi.atom.service

/**
 * Detects the MIME type of a download URL from its file extension.
 *
 * This replaces the Apache Tika based detection of
 * `de.ingrid.iface.atomDownloadService.DefaultDatasetFeedEntryProducer#getTypeByLinkage`.
 */
object MimeTypes {

    /** Detects the MIME type of the given URL from its file extension. */
    fun detect(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val name = path.substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase()
        return byExtension(extension)
    }

    private fun byExtension(extension: String): String =
        when (extension) {
            "atom" -> "application/atom+xml"
            "xml" -> "application/xml"
            "gml" -> "application/gml+xml"
            "gmz" -> "application/x-gmz"
            "zip" -> "application/zip"
            "gz", "gzip" -> "application/gzip"
            "7z" -> "application/x-7z-compressed"
            "rar" -> "application/vnd.rar"
            "tar" -> "application/x-tar"
            "json", "geojson" -> "application/json"
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "html", "htm" -> "text/html"
            "txt" -> "text/plain"
            "wfs", "wms", "sos", "wcs" -> "application/xml"
            "kml" -> "application/vnd.google-earth.kml+xml"
            "kmz" -> "application/vnd.google-earth.kmz"
            "tif", "tiff" -> "image/tiff"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "dwg" -> "image/vnd.dwg"
            "dxf" -> "image/vnd.dxf"
            "shp" -> "application/octet-stream"
            "gpkg" -> "application/geopackage+sqlite3"
            "sqlite", "db" -> "application/vnd.sqlite3"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "doc", "docx" -> "application/msword"
            else -> "application/octet-stream"
        }
}

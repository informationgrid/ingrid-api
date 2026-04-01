package de.ingrid.ingridapi.ogc.records.items

object ItemsExporterFactory {
    fun create(format: ItemExportFormat): ItemsExporter =
        when (format) {
            ItemExportFormat.HTML -> HtmlItemsExporter()

            //            ItemExportFormat.ISO -> IsoItemsExporter()
            ItemExportFormat.INDEX -> IndexItemsExporter()
//            ItemExportFormat.GEOJSON -> GeoJsonItemsExporter()
        }
}

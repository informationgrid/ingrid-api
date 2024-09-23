package de.ingrid.ingridapi.portal

val getCatalogsQuery =
    """
    {
      "size": 0,
      "aggs": {
        "catalogs": {
          "terms": {
            "field": "_index",
            "exclude": ["ingrid_meta"],
            "size": 100
            
          },
          "aggs": {
            "info": {
              "top_hits": {
                "size": 1,
                "_source": {
                  "include": [
                    "dataSourceName","partner", "plugId", "datatype"
                  ]
                }
              }
            }
          }
        }
      }
    }
    """.trimIndent()

fun getHierarchy(
    index: String,
    parent: String? = null,
): String {
    //language=JSON
    return """
        {
          "size": 1000,
          "query": {
            "bool": {
              "filter": [
                {
                  "term": {
                    "_index": "$index"
                  }
                }
              ],
              "must": {
                "term": {
                  "parent.object_node.obj_uuid": "$parent"
                }
              }
            }
          }
        } 
        """.trimIndent()
}

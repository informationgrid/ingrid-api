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
): String =
    if (parent == null) {
        //language=JSON
        """
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
              "should": [
                {
                  "term": {
                    "parent.object_node.obj_uuid": ""
                  }
                },
                {
                  "term": {
                    "parent.address_node.addr_uuid": ""
                  }
                },
                {
                  "bool": {
                    "must_not": [
                      {
                        "exists": {
                          "field": "parent.object_node.obj_uuid"
                        }
                      },
                      {
                        "exists": {
                          "field": "parent.address_node.addr_uuid"
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
        } 
        """.trimIndent()
    } else {
        """
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
              "should": [
                {
                  "term": {
                    "parent.object_node.obj_uuid": "$parent"
                  }
                },
                {
                  "term": {
                    "parent.address_node.addr_uuid": "$parent"
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()
    }

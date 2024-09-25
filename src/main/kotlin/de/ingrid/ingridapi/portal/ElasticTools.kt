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
              "must": [
                {
                  "filter": [
                    {
                      "term": {
                        "_index": "$index"
                      }
                    }
                  ],
                  "should": [
                    {
                      "bool": {
                        "must_not": {
                          "exists": {
                            "field": "parentObj"
                          }
                        }
                      },
                    {
                      "bool": {
                        "must_not": {
                          "exists": {
                            "field": "parentAddr"
                          }
                        }
                      }
                    },
                    {
                      "term": {
                        "parentObj": ""
                      }
                    },
                    {
                      "term": {
                        "parentAddr": ""
                      }
                    }
                    }
                  ],
                  "minimum_should_match": 1
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
              "must": {
                "bool": {
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
                  ],
                  "minimum_should_match": 1
                }
              }
            }
          }
        }
        """.trimIndent()
    }

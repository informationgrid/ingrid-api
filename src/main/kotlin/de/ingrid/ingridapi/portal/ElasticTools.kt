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
              "must": [
                {
                  "bool": {
                    "should": [
                      {
                        "bool": {
                          "must": [
                            {
                              "term": {
                                "datatype": "address"
                              }
                            },
                            {
                              "bool": {
                                "should": [
                                  {
                                    "bool": {
                                      "must_not": {
                                        "exists": {
                                          "field": "parent.address_node.addr_uuid"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "term": {
                                      "parent.address_node.addr_uuid": ""
                                    }
                                  }
                                ],
                                "minimum_should_match": 1
                              }
                            }
                          ],
                          "must_not": [
                            {
                              "exists": {
                                "field": "parent.object_node.obj_uuid"
                              }
                            }
                          ]
                        }
                      },
                      {
                        "bool": {
                          "must_not": [
                            {
                              "exists": {
                                "field": "parent.address_node.addr_uuid"
                              }
                            },
                            {
                              "term": {
                                "datatype": "address"
                              }
                            }
                          ],
                          "must": [
                            {
                              "bool": {
                                "should": [
                                  {
                                    "bool": {
                                      "must_not": {
                                        "exists": {
                                          "field": "parent.object_node.obj_uuid"
                                        }
                                      }
                                    }
                                  },
                                  {
                                    "term": {
                                      "parent.object_node.obj_uuid": ""
                                    }
                                  }
                                ],
                                "minimum_should_match": 1
                              }
                            }
                          ]
                        }
                      }
                    ],
                    "minimum_should_match": 1
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

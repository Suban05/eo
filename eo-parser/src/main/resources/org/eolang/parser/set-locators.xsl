<?xml version="1.0" encoding="UTF-8"?>
<!--
The MIT License (MIT)

Copyright (c) 2016-2022 Objectionary.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:eo="https://www.eolang.org" xmlns:xs="http://www.w3.org/2001/XMLSchema" id="set-locators" version="2.0">
  <!--
  Here we go through all objects and add @loc attributes
  to all of them. The value of the attribute is a unique locator
  of the object.
  -->
  <xsl:output encoding="UTF-8" method="xml"/>
  <xsl:function name="eo:locator" as="xs:string">
    <xsl:param name="program" as="node()"/>
    <xsl:param name="o" as="node()"/>
    <xsl:variable name="ret">
      <xsl:choose>
        <xsl:when test="$o/parent::o">
          <xsl:value-of select="eo:locator($program, $o/parent::o)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>Φ</xsl:text>
          <xsl:if test="not(contains($o/@base, '.')) and $program/metas/meta[head='package']">
            <xsl:text>.</xsl:text>
            <xsl:value-of select="$program/metas/meta[head='package']/tail"/>
          </xsl:if>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:text>.</xsl:text>
      <xsl:choose>
        <xsl:when test="$o/@name">
          <xsl:choose>
            <xsl:when test="$o/@name = '@'">
              <xsl:text>φ</xsl:text>
            </xsl:when>
            <xsl:when test="$o/@name = '^'">
              <xsl:text>ρ</xsl:text>
            </xsl:when>
            <xsl:when test="$o/@name = '$'">
              <xsl:text>ξ</xsl:text>
            </xsl:when>
            <xsl:when test="$o/@name = 'Q'">
              <xsl:text>Φ</xsl:text>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="$o/@name"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:when>
        <xsl:otherwise>
          <xsl:choose>
            <xsl:when test="name($o) = 'o'">
              <xsl:text>α</xsl:text>
              <xsl:value-of select="count($o/preceding-sibling::o)"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:text>ν0</xsl:text>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:variable>
    <xsl:value-of select="$ret"/>
  </xsl:function>
  <xsl:template match="o">
    <xsl:copy>
      <xsl:attribute name="loc" select="eo:locator(/program, .)"/>
      <xsl:apply-templates select="node()|@* except @loc"/>
    </xsl:copy>
  </xsl:template>
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>

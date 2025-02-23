<?xml version="1.0" encoding="UTF-8"?>
<!--
 * SPDX-FileCopyrightText: Copyright (c) 2016-2025 Objectionary.com
 * SPDX-License-Identifier: MIT
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" id="resolve-aliases" version="2.0">
  <!--
  Here we go through all objects that are not methods or have
  composite FQN and try to find their references in aliases.
  If we find them, we change their @base attributes. If not,
  we decide that they are in org.eolang package and also change
  the @base attribute.
  -->
  <xsl:output encoding="UTF-8" method="xml"/>
  <xsl:template match="o[@base]">
    <xsl:apply-templates select="." mode="with-base"/>
  </xsl:template>
  <xsl:template match="o[not(contains(@base, '.'))]" mode="with-base">
    <xsl:variable name="o" select="."/>
    <xsl:copy>
      <xsl:attribute name="base">
        <xsl:variable name="meta" select="/program/metas/meta[head='alias' and part[1] = $o/@base]"/>
        <xsl:choose>
          <xsl:when test="$meta">
            <xsl:variable name="tail" select="$meta/part[last()]"/>
            <xsl:value-of select="$tail[1]"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="$o/@base"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:attribute>
      <xsl:apply-templates select="node()|@* except @base"/>
    </xsl:copy>
  </xsl:template>
  <xsl:template match="node()|@*" mode="#all">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>
</xsl:stylesheet>

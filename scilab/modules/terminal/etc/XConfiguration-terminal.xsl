<?xml version='1.0' encoding='utf-8'?>
<xsl:stylesheet version ="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                >

  <xsl:template match="terminal-settings">
    <Title text="_(Terminal Settings)">
      <Grid>
        <Label gridx="1" gridy="1" weightx="0" text="_(Shell (blank = $SHELL): )"/>
        <Entry gridx="2" gridy="1" weightx="1" anchor="above_baseline"
               listener="EntryListener"
               text="{@shell}">
          <entryChanged choose="shell">
            <xsl:call-template name="context"/>
          </entryChanged>
        </Entry>

        <Label gridx="1" gridy="2" weightx="0" text="_(Starting directory (blank = current): )"/>
        <FileSelector gridx="2" gridy="2" weightx="1" anchor="above_baseline"
                      listener="EntryListener"
                      href="{@start-dir}"
                      mask="*.*"
                      desc="_(Directories)"
                      dir-selection="true"
                      check-entry="false">
          <entryChanged choose="start-dir">
            <xsl:call-template name="context"/>
          </entryChanged>
        </FileSelector>

        <Label gridx="1" gridy="3" weightx="0" text="_(Scrollback (lines): )"/>
        <NumericalSpinner gridx="2"
                          gridy="3"
                          weightx="0"
                          min-value="0"
                          increment="1000"
                          length="6"
                          listener="ActionListener"
                          value="{@scrollback-lines}">
          <actionPerformed choose="scrollback-lines">
            <xsl:call-template name="context"/>
          </actionPerformed>
        </NumericalSpinner>

        <Checkbox checked="{@audible-bell}" selected-value="true" unselected-value="false"
                  listener="ActionListener" text="_(Audible bell)"
                  gridx="1" gridy="4" fill="none" weightx="0" anchor="west">
          <actionPerformed choose="audible-bell">
            <xsl:call-template name="context"/>
          </actionPerformed>
        </Checkbox>
      </Grid>
    </Title>
    <VSpace height="10"/>
  </xsl:template>

  <xsl:template match="terminal-experimental">
    <Title text="_(Coming soon)">
      <Grid>
        <Checkbox checked="{@follow-cd}" selected-value="true" unselected-value="false"
                  enable="false" listener="ActionListener"
                  text="_(Terminal follows Scilab's current directory)"
                  gridx="1" gridy="1" fill="none" weightx="0" anchor="west">
          <actionPerformed choose="follow-cd">
            <xsl:call-template name="context"/>
          </actionPerformed>
        </Checkbox>
        <Checkbox checked="{@send-to-terminal}" selected-value="true" unselected-value="false"
                  enable="false" listener="ActionListener"
                  text="_(Send selected console text to the terminal)"
                  gridx="1" gridy="2" fill="none" weightx="0" anchor="west">
          <actionPerformed choose="send-to-terminal">
            <xsl:call-template name="context"/>
          </actionPerformed>
        </Checkbox>
      </Grid>
    </Title>
    <VSpace height="10"/>
  </xsl:template>

</xsl:stylesheet>

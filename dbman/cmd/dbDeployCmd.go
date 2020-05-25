//   Onix Config Manager - Dbman
//   Copyright (c) 2018-2020 by www.gatblau.org
//   Licensed under the Apache License, Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0
//   Contributors to this project, hereby assign copyright in this code to the project,
//   to be licensed under the same terms as the rest of the code.
package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
)

type DbDeployCmd struct {
	cmd *cobra.Command
}

func NewDbDeployCmd() *DbDeployCmd {
	c := &DbDeployCmd{
		&cobra.Command{
			Use:   "deploy [version]",
			Short: "deploys a database schema",
			Long:  `if version is not specified, then it deploys the latest schema`,
		},
	}
	c.cmd.Run = c.Run
	return c
}

func (c *DbDeployCmd) Run(cmd *cobra.Command, args []string) {
	fmt.Println("deploy called")
}
